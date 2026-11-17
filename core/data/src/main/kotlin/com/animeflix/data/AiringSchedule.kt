package com.animeflix.data

import android.content.Context
import com.animeflix.model.Anime
import com.animeflix.model.EpisodeAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

data class AiringAnime(val anime: Anime, val latestNumber: String, val postedAt: Long) {
    val latestUrl get() = "${anime.sourceUrl.trimEnd('/')}/$latestNumber"
    fun caughtUp(status: EpisodeAvailability?, watched: Set<String>): Boolean = status?.caughtUp == true && latestUrl in watched
}

fun scheduleDay(time: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Int =
    Calendar.getInstance(zone).apply { timeInMillis = time }.get(Calendar.DAY_OF_WEEK).let { if(it == Calendar.SUNDAY) 7 else it - 1 }

fun scheduleDays(today: Int): List<Int> { require(today in 1..7); return (0..6).map { ((today - 1 - it + 7) % 7) + 1 } }

fun orderAiring(items: List<AiringAnime>, today: Int, zone: TimeZone = TimeZone.getDefault()): List<AiringAnime> {
    fun minute(time: Long) = Calendar.getInstance(zone).apply { timeInMillis = time }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    return items.sortedWith(compareBy<AiringAnime> { (today - scheduleDay(it.postedAt, zone) + 7) % 7 }
        .thenBy { minute(it.postedAt) }.thenBy { it.anime.title.lowercase() })
}

fun airingDayLabel(day: Int, today: Int): String = (if(day == today) "Hoy · " else "") + listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")[day - 1]

internal fun parseAiringSchedule(html: String): List<AiringAnime> {
    val rows = publicObjects(html, "data:{media:[").mapNotNull { block ->
        val slug = publicDataString(block, "slug")?.takeIf { Regex("[a-zA-Z0-9_-]+").matches(it) } ?: return@mapNotNull null
        val title = publicDataString(block, "title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val id = Regex("^\\{id:([0-9]+)").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
        val episode = Regex("latestEpisode:\\{([^}]+)\\}").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
        val number = Regex("(?:^|,)number:([0-9]+(?:\\.[0-9]+)?)(?=[,}])").find(episode)?.groupValues?.get(1) ?: return@mapNotNull null
        val posted = publicDataString(episode, "createdAt")?.let(::publicationTime) ?: return@mapNotNull null
        AiringAnime(Anime("av1:$slug", title, publicDataString(block, "synopsis") ?: "Sin sinopsis disponible.",
            posterUrl = "https://cdn.animeav1.com/covers/$id.jpg", sourceUrl = "https://animeav1.com/media/$slug"), number, posted)
    }.distinctBy { it.anime.id }
    if(rows.isEmpty()) throw IOException("No se reconoce el horario")
    return rows
}

data class AiringScheduleState(val items: List<AiringAnime> = emptyList(), val loading: Boolean = false, val error: String? = null)

class AiringSchedule private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("airing_schedule_v1", Context.MODE_PRIVATE)
    private val local = LocalAnimeRepository.create(context)
    private val mutex = Mutex()
    private fun cached(): List<AiringAnime> = runCatching {
        val rows = JSONArray(preferences.getString("items", "[]"))
        (0 until rows.length()).mapNotNull { i -> val row = rows.getJSONObject(i)
            val source = row.getString("source")
            val number = row.getString("number")
            if(!AnimeAv1Episodes.isMediaUrl(source) || !AnimeAv1Episodes.isEpisodeUrl("$source/$number") || row.getLong("postedAt") <= 0) null
            else AiringAnime(Anime("av1:" + source.substringAfterLast('/'), row.getString("title"), row.optString("synopsis"),
                posterUrl = row.optString("poster"), sourceUrl = source), number, row.getLong("postedAt"))
        }
    }.getOrDefault(emptyList())
    private val mutableState = MutableStateFlow(AiringScheduleState(cached()))
    val state = mutableState.asStateFlow()
    private val cacheListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if(key == "items" || key == null) mutableState.value = mutableState.value.copy(items = cached())
    }
    init { preferences.registerOnSharedPreferenceChangeListener(cacheListener) }
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val age = System.currentTimeMillis() - preferences.getLong("updatedAt", 0)
        if(!force && age in 0 until 15 * 60 * 1000L && mutableState.value.items.isNotEmpty()) return@withLock
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        try {
            val items = withContext(Dispatchers.IO) {
                val html = Jsoup.connect("https://animeav1.com/horario").userAgent("AnimeFlix (weekly schedule)").timeout(12_000).maxBodySize(3_000_000).get().outerHtml()
                val rows = parseAiringSchedule(html)
                local.cacheCatalog(rows.map { it.anime })
                val json = JSONArray().apply { rows.forEach { row -> put(JSONObject().put("source", row.anime.sourceUrl).put("title", row.anime.title)
                    .put("synopsis", row.anime.synopsis).put("poster", row.anime.posterUrl).put("number", row.latestNumber).put("postedAt", row.postedAt)) } }
                if(!preferences.edit().putString("items", json.toString()).putLong("updatedAt", System.currentTimeMillis()).commit()) throw IOException("Schedule cache unavailable")
                rows
            }
            mutableState.value = AiringScheduleState(items)
        } catch(failure: Exception) {
            if(failure is CancellationException) { mutableState.value = mutableState.value.copy(loading = false); throw failure }
            mutableState.value = mutableState.value.copy(loading = false, error = "No se pudo actualizar En emisión. Se conserva el último horario guardado.")
        }
    }
    companion object {
        @Volatile private var instance: AiringSchedule? = null
        fun create(context: Context): AiringSchedule = instance ?: synchronized(this) {
            instance ?: AiringSchedule(context.applicationContext).also { instance = it }
        }
    }
}
