package com.animeflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

data class Dorama(val slug: String, val title: String, val poster: String = "") {
    val url get() = "${DoramasCatalog.ORIGIN}/doramas/$slug"
}

data class DoramaEpisode(val slug: String, val season: Int, val number: Int) {
    val url get() = "${DoramasCatalog.ORIGIN}/capitulos/$slug-${season}x$number"
}
data class DoramaPublication(val episode: DoramaEpisode, val title: String, val poster: String)

data class DoramaAvailability(
    val upcoming: Boolean = false,
    val airDateMs: Long? = null,
    val audio: List<String> = emptyList(),
    val checked: Boolean = false
)

object DoramasCatalog {
    const val ORIGIN = "https://doramasflix.co"
    private val seriesPath = Regex("/doramas/([a-z0-9-]+)")
    private val episodePath = Regex("/capitulos/([a-z0-9-]+)-(\\d+)x(\\d+)")
    private val checks = Semaphore(3)
    private val availabilityCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, DoramaAvailability>>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, DoramaAvailability>>?) = size > 200
        }
    )

    fun series(url: String): String? = seriesPath.matchEntire(url.removePrefix(ORIGIN))
        ?.groupValues?.get(1)

    fun episode(url: String): DoramaEpisode? {
        val (_, slug, season, number) = episodePath.matchEntire(url.removePrefix(ORIGIN))?.groupValues ?: return null
        return DoramaEpisode(slug, season.toIntOrNull() ?: return null, number.toIntOrNull() ?: return null)
    }

    suspend fun catalog(page: Int): List<Dorama> = withContext(Dispatchers.IO) {
        seriesCards(document("$ORIGIN/doramas?page=$page"))
    }

    suspend fun search(query: String): List<Dorama> = withContext(Dispatchers.IO) {
        val term = URLEncoder.encode(query.trim(), "UTF-8")
        seriesCards(document("$ORIGIN/buscar?q=$term"))
    }

    private fun seriesCards(page: Document): List<Dorama> =
        page.select("a[href][aria-label]").mapNotNull { link ->
            val slug = series(link.attr("href")) ?: return@mapNotNull null
            val title = link.attr("aria-label").removePrefix("Ver ").trim()
            val poster = link.selectFirst("img[src]")?.absUrl("src").orEmpty()
            Dorama(slug, title, poster).takeIf { title.isNotEmpty() }
        }.distinctBy { it.slug }

    suspend fun episodes(dorama: Dorama): List<DoramaEpisode> = withContext(Dispatchers.IO) {
        document(dorama.url).select("a[href]").mapNotNull { episode(it.attr("href")) }
            .filter { it.slug == dorama.slug }.distinct()
            .sortedWith(compareBy(DoramaEpisode::season, DoramaEpisode::number))
    }

    suspend fun recent(): List<DoramaPublication> = withContext(Dispatchers.IO) {
        document("$ORIGIN/ultimos-episodios").select("a[href]").mapNotNull { link ->
            val episode = episode(link.attr("href")) ?: return@mapNotNull null
            val image = link.selectFirst("img[src]")
            val title = link.text().trim().ifBlank { image?.attr("alt").orEmpty().trim() }
                .removeSuffix(" ${episode.season}x${episode.number}").trim()
                .ifBlank { episode.slug.replace('-', ' ').replaceFirstChar { it.uppercase() } }
            DoramaPublication(episode, title, image?.absUrl("src").orEmpty())
        }.distinctBy { it.episode }
    }

    suspend fun availability(episode: DoramaEpisode): DoramaAvailability = checks.withPermit {
        val now = System.currentTimeMillis()
        val cached = availabilityCache.get(episode.url)
        if (cached != null && now - cached.first < 300_000) return@withPermit cached.second
        val result = withContext(Dispatchers.IO) { readAvailability(document(episode.url), now) }
        if (result.checked) availabilityCache[episode.url] = now to result
        result
    }

    internal fun readAvailability(page: Document, now: Long): DoramaAvailability {
        val payload = page.select("script").asSequence().mapNotNull { script ->
            val data = script.data().substringAfter("self.__next_f.push(", "").trim().removeSuffix(")")
            if (!data.startsWith("[1,")) null else runCatching { JSONArray(data).getString(1) }.getOrNull()
        }.joinToString("\n")
        val airDate = Regex("\"air_date\":\"?(\\d{13})").findAll(payload.substringBefore("\"initialLinks\""))
            .lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
        val upcoming = page.select("h2,h3,h4").any { it.text().contains("aún no ha sido emitido", ignoreCase = true) } ||
            (airDate != null && airDate > now)
        if (upcoming) return DoramaAvailability(upcoming = true, airDateMs = airDate?.takeIf { it > now }, checked = true)

        fun array(name: String): JSONArray? {
            val value = payload.substringAfter("\"$name\":", "")
            return runCatching { JSONTokener(value).nextValue() as? JSONArray }.getOrNull()
        }
        fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
        val links = array("initialLinks") ?: return DoramaAvailability()
        val audio = array("listLanguages")?.objects().orEmpty() + array("langs")?.objects().orEmpty()
        val languages = audio.associate { it.optString("code_flix") to it.optString("name").trim() }
        val available = links.objects().filter {
            it.optString("link").isNotBlank()
        }.map {
            val language = languages[it.optString("lang")].orEmpty()
            when (language.lowercase()) {
                "coreano" -> "Coreano · sub. español"
                "latino" -> "Audio latino"
                "" -> "Audio sin identificar"
                else -> language
            }
        }.distinct()
        return DoramaAvailability(audio = available, checked = true)
    }

    private fun document(url: String): Document = Jsoup.connect(url).timeout(15_000)
        .maxBodySize(2_000_000).get()
}
