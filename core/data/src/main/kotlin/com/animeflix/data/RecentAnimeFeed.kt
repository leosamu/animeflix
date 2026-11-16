package com.animeflix.data

import android.content.Context
import com.animeflix.model.Anime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/** Public episode feed order, not viewing history or the order in which we checked a series. */
internal fun parseRecentAnime(html: String): List<Anime> {
    val document = Jsoup.parse(html, "https://animeav1.com/")
    val section = document.select("section").firstOrNull { section ->
        section.select("h2").any { it.text().equals("Episodios", ignoreCase = true) }
    } ?: throw IOException("No se reconoce la sección de episodios recientes")
    val items = section.select("article").mapNotNull { article ->
        val url = article.select("a[href]").map { it.absUrl("href") }.firstOrNull(AnimeAv1Episodes::isEpisodeUrl)
            ?: return@mapNotNull null
        val title = article.selectFirst("header")?.text()?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val media = url.substringBeforeLast('/')
        val thumbnail = article.selectFirst("img")?.absUrl("src").orEmpty()
        // The source uses the media ID for both thumbnails and cover art.
        val poster = if (Regex("https://cdn\\.animeav1\\.com/thumbnails/[0-9]+\\.jpg").matches(thumbnail))
            thumbnail.replace("/thumbnails/", "/covers/") else ""
        Anime("av1:" + media.substringAfterLast('/'), title, "Sin sinopsis disponible.", posterUrl = poster, sourceUrl = media)
    }.distinctBy { it.id }
    if (items.isEmpty()) throw IOException("La lista de episodios recientes está vacía o ha cambiado")
    return items
}

internal fun prioritizeRecent(items: List<Anime>, recentIds: List<String>): List<Anime> {
    val rank = recentIds.withIndex().associate { it.value to it.index }
    return items.sortedWith(compareBy<Anime> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }.thenBy { it.id })
}

internal fun recentWindow(current: List<String>, previous: List<String>): List<String> {
    val fresh = current.distinct()
    return fresh + previous.distinct().filterNot { it in fresh }.take((24 - fresh.size).coerceAtLeast(0))
}

data class RecentCatalogState(val ids: List<String>, val error: String? = null)

/** Shared device cache: the public publication feed is identical for every viewing profile. */
class RecentAnimeFeed private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("recent_anime_feed", Context.MODE_PRIVATE)
    private val local = LocalAnimeRepository.create(context)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RecentCatalogState(preferences.getString("ids", "").orEmpty().lines().filter { it.isNotBlank() }))
    val state = mutableState.asStateFlow()

    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val age = System.currentTimeMillis() - preferences.getLong("updatedAt", 0)
        if (!force && preferences.getInt("version", 0) == 2 && age in 0 until 15 * 60 * 1000L && mutableState.value.ids.isNotEmpty()) return@withLock
        try {
            withContext(Dispatchers.IO) {
                val html = Jsoup.connect("https://animeav1.com/").userAgent("AnimeFlix (Android catalog client)")
                    .timeout(20_000).maxBodySize(3_000_000).get().outerHtml()
                val fresh = parsePublications(html)
                if (fresh.isEmpty()) throw IOException("No se reconocen las fechas de novedades")
                val cached = runCatching {
                    val rows = JSONArray(preferences.getString("publications", "[]"))
                    (0 until rows.length()).map { index -> val row = rows.getJSONObject(index)
                        val slug = row.getString("slug")
                        RecentPublication(Anime("av1:$slug", row.getString("title"), "", sourceUrl = "https://animeav1.com/media/$slug"), row.getString("number"), row.getLong("at"))
                    }
                }.getOrDefault(emptyList())
                val supplement = try {
                    val schedule = Jsoup.connect("https://animeav1.com/horario").userAgent("AnimeFlix (Android catalog client)")
                        .timeout(8_000).maxBodySize(3_000_000).get().outerHtml()
                    val scheduleItems = parsePublications(schedule, schedule = true)
                    val candidates = scheduleItems.sortedByDescending { it.publishedAt }
                        .distinctBy { it.anime.id }.filterNot { candidate -> fresh.any { it.anime.id == candidate.anime.id } }.take(12)
                    android.util.Log.d("AnimeFlixRecent", "Home=${fresh.size} schedule=${scheduleItems.size} candidates=${candidates.size}")
                    val network = Semaphore(3)
                    coroutineScope { candidates.map { candidate -> async {
                        network.withPermit { try {
                            val page = Jsoup.connect("${candidate.anime.sourceUrl}/${candidate.number}").userAgent("AnimeFlix (Android catalog client)")
                                .timeout(6_000).maxBodySize(2_000_000).get().outerHtml()
                            episodeReleaseTime(page, candidate.number)?.let { candidate.copy(publishedAt = it) }
                        } catch (failure: Exception) { if (failure is CancellationException) throw failure; android.util.Log.d("AnimeFlixRecent", "Date unavailable: ${failure.javaClass.simpleName}"); null } }
                    } }.awaitAll().filterNotNull() }
                } catch (failure: Exception) { if (failure is CancellationException) throw failure; android.util.Log.d("AnimeFlixRecent", "Schedule unavailable: ${failure.javaClass.simpleName}"); emptyList() }
                val publications = recentPublications(fresh + supplement, cached)
                android.util.Log.d("AnimeFlixRecent", "Verified=${supplement.size} visible=${publications.size}")
                val items = publications.map { it.anime }
                // Do not replace full descriptions, cover art or favorites already cached from the catalogue.
                local.cacheNewCatalog(items)
                val ids = items.map { it.id }
                val records = JSONArray().apply { publications.forEach { item -> put(JSONObject().put("slug", item.anime.sourceUrl.substringAfterLast('/'))
                    .put("title", item.anime.title).put("number", item.number).put("at", item.publishedAt)) } }
                if (!preferences.edit().putString("ids", ids.joinToString("\n"))
                        .putString("publications", records.toString()).putInt("version", 2)
                        .putLong("updatedAt", System.currentTimeMillis()).commit()) throw IOException("No se pudo guardar el orden")
                mutableState.value = RecentCatalogState(ids, if(ids.size < 24) "La fuente no ha permitido completar las 24 novedades. Se muestran las fechas disponibles." else null)
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.value = mutableState.value.copy(error = "No se pudieron actualizar las novedades. Se conserva el último orden guardado.")
        }
    }

    fun order(items: List<Anime>, ids: List<String>): List<Anime> = prioritizeRecent(items, ids)

    companion object {
        @Volatile private var instance: RecentAnimeFeed? = null
        fun create(context: Context): RecentAnimeFeed = instance ?: synchronized(this) {
            instance ?: RecentAnimeFeed(context.applicationContext).also { instance = it }
        }
    }
}
