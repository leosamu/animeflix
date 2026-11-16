package com.animeflix.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlin.math.abs

data class SkipInterval(val type: String, val startMs: Long, val endMs: Long) {
    fun target(durationMs: Long): Long = endMs.coerceAtMost((durationMs - 2_500).coerceAtLeast(0))
    fun active(positionMs: Long, durationMs: Long): Boolean = positionMs >= startMs && positionMs < target(durationMs)
}

internal fun parseAnimeMalId(html: String, slug: String): Long? {
    if (!Regex("[a-zA-Z0-9_-]+").matches(slug)) return null
    return Regex("slug:\"${Regex.escape(slug)}\",malId:([0-9]+)").find(html)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
}

internal fun parseSkipTimes(raw: String, durationMs: Long): List<SkipInterval> {
    if (durationMs !in 60_000..86_400_000 || raw.length > 256_000) return emptyList()
    return runCatching {
        val json = JSONObject(raw)
        if (!json.optBoolean("found")) return emptyList()
        val results = json.getJSONArray("results")
        (0 until results.length().coerceAtMost(100)).mapNotNull { index ->
            runCatching {
                val row = results.getJSONObject(index)
                val type = row.getString("skipType")
                val duration = row.getDouble("episodeLength") * 1_000
                val interval = row.getJSONObject("interval")
                val start = interval.getDouble("startTime") * 1_000
                val end = interval.getDouble("endTime") * 1_000
                if (type !in setOf("op", "ed") || !duration.isFinite() || abs(duration - durationMs) > 2_000 ||
                    !start.isFinite() || !end.isFinite() || start < 0 || end <= start || end > durationMs + 2_000 ||
                    end - start !in 10_000.0..600_000.0) return@mapNotNull null
                Triple(abs(duration - durationMs), type, SkipInterval(type, start.toLong(), end.toLong().coerceAtMost(durationMs)))
            }.getOrNull()
        }.sortedBy { it.first }.distinctBy { it.second }.map { it.third }.sortedBy { it.startMs }
    }.getOrDefault(emptyList())
}

/** Read-only public metadata. Errors never block playback; offline playback never contacts either service. */
class AniSkipTimes(context: Context) {
    private val cache = context.applicationContext.getSharedPreferences("aniskip_times_v1", Context.MODE_PRIVATE)

    suspend fun load(episodeUrl: String, durationMs: Long, offline: Boolean): List<SkipInterval> = withContext(Dispatchers.IO) {
        if (!AnimeAv1Episodes.isEpisodeUrl(episodeUrl) || durationMs !in 60_000..86_400_000) return@withContext emptyList()
        // The response contains all editions: reuse it across providers, then validate against this video's duration.
        val key = episodeUrl
        val stored = runCatching { JSONObject(cache.getString(key, "").orEmpty()) }.getOrNull()
        val age = System.currentTimeMillis() - (stored?.optLong("at") ?: 0)
        if (stored != null && (offline || age in 0 until stored.optLong("ttl")))
            return@withContext parseSkipTimes(stored.optString("response"), durationMs)
        if (offline) return@withContext emptyList()
        try {
            val media = episodeUrl.substringBeforeLast('/')
            val malKey = "mal:$media"
            val mal = cache.getLong(malKey, 0).takeIf { it > 0 } ?: run {
                val response = Jsoup.connect(media).userAgent("AnimeFlix (skip metadata)").timeout(5_000).maxBodySize(2_000_000).get().outerHtml()
                parseAnimeMalId(response, media.substringAfterLast('/'))?.also { cache.edit().putLong(malKey, it).apply() }
            }
            val response = if (mal != null) Jsoup.connect("https://api.aniskip.com/v2/skip-times/$mal/${episodeUrl.substringAfterLast('/')}")
                .data("types[]", "op", "types[]", "ed", "episodeLength", "0")
                .userAgent("AnimeFlix (skip metadata)").ignoreContentType(true).ignoreHttpErrors(true)
                .timeout(4_000).maxBodySize(256_000).execute() else null
            if (response != null && response.statusCode() !in setOf(200, 404)) error("Metadata unavailable")
            val raw = response?.body() ?: "{\"found\":false}"
            val intervals = parseSkipTimes(raw, durationMs)
            val ttl = if (intervals.isEmpty()) 24 * 60 * 60 * 1_000L else 7 * 24 * 60 * 60 * 1_000L
            cache.edit().putString(key, JSONObject().put("at", System.currentTimeMillis()).put("ttl", ttl).put("response", raw).toString()).apply()
            intervals
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            cache.edit().putString(key, JSONObject().put("at", System.currentTimeMillis()).put("ttl", 5 * 60 * 1_000L)
                .put("response", "{\"found\":false}").toString()).apply()
            emptyList()
        }
    }
}
