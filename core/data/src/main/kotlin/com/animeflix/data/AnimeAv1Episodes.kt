package com.animeflix.data

import com.animeflix.model.Episode
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI
import com.animeflix.model.EpisodeInventory
import java.io.IOException
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import kotlinx.coroutines.CancellationException

class AnimeAv1Episodes {
    suspend fun inventory(mediaUrl: String, previous: EpisodeInventory? = null): EpisodeInventory = withContext(Dispatchers.IO) {
        require(isMediaUrl(mediaUrl))
        val base = mediaUrl.trimEnd('/')
        val html = fetch(base)
        val episodes = parseEpisodes(html, base)
        if(!html.contains("episodes:[") && episodes.isEmpty()) throw IOException("No se reconoce la lista de episodios")
        val latest = episodes.lastOrNull()
        var published = previous?.takeIf { it.mediaUrl == base && it.episodes.lastOrNull()?.pageUrl == latest?.pageUrl }?.latestPublishedAt
        if(latest != null && published == null) {
            try { published = parsePublishedAt(fetch(latest.pageUrl), latest.number) }
            catch(failure: Exception) { if(failure is CancellationException) throw failure }
        }
        EpisodeInventory(base, episodes, published, System.currentTimeMillis())
    }
    suspend fun episodes(mediaUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        require(isMediaUrl(mediaUrl))
        parseEpisodes(fetch(mediaUrl), mediaUrl)
    }

    suspend fun servers(episodeUrl: String): List<PlaybackServer> = withContext(Dispatchers.IO) {
        require(isEpisodeUrl(episodeUrl))
        parseServers(fetch(episodeUrl), episodeUrl)
    }

    private fun fetch(url: String): String = Jsoup.connect(url)
        .userAgent("AnimeFlix/0.3 (Android client)").timeout(20_000).maxBodySize(3_000_000).get().outerHtml()

    companion object {
        internal fun parsePublishedAt(html: String, number: String): Long? {
            val block = html.substringAfter("episode:{", "").substringBefore("},embeds:")
            val actual = Regex("(?:^|,)number:([0-9]+(?:\\.[0-9]+)?)(?=[,}])").find(block)?.groupValues?.get(1) ?: return null
            if(actual.toBigDecimal().compareTo(number.toBigDecimal()) != 0) return null
            val raw = Regex("publishedAt:\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: return null
            val parts = Regex("(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)").matchEntire(raw) ?: return null
            val zone = parts.groupValues[4].let { if(it == "Z") "+0000" else it.replace(":", "").padEnd(5, '0') }
            val normalized = "${parts.groupValues[1]}T${parts.groupValues[2]}.${parts.groupValues[3].take(3).padEnd(3,'0')}$zone"
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            return format.parse(normalized, position)?.time?.takeIf { it > 0 && position.index == normalized.length }
        }
        fun isMediaUrl(url: String): Boolean = Regex("https://animeav1\\.com/media/[a-zA-Z0-9_-]+/?").matches(url)
        fun isEpisodeUrl(url: String): Boolean = Regex("https://animeav1\\.com/media/[a-zA-Z0-9_-]+/[0-9]+(?:\\.[0-9]+)?").matches(url)
        fun isHttpsUrl(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
        }.getOrDefault(false)

        internal fun parseEpisodes(html: String, mediaUrl: String): List<Episode> {
            require(isMediaUrl(mediaUrl))
            val base = mediaUrl.trimEnd('/')
            val document = Jsoup.parse(html, base)
            // Only read the published episode array; never evaluate embedded JavaScript.
            val data = Regex("episodes:\\[([^]]*)]").find(html)?.groupValues?.get(1).orEmpty()
            val numbers = Regex("(?:^|[,{}])number:([0-9]+(?:\\.[0-9]+)?)(?=[,}])")
                .findAll(data).map { it.groupValues[1] }.toMutableSet()
            document.select("a[href]").forEach { link ->
                val url = link.absUrl("href")
                if (url.startsWith("$base/")) {
                    val number = url.removePrefix("$base/")
                    if (Regex("[0-9]+(?:\\.[0-9]+)?").matches(number)) numbers.add(number)
                }
            }
            return numbers.filter { it.toBigDecimal() > java.math.BigDecimal.ZERO }
                .sortedBy { it.toBigDecimal() }.map { Episode(it, "$base/$it") }
        }

        internal fun parseServers(html: String, episodeUrl: String): List<PlaybackServer> {
            require(isEpisodeUrl(episodeUrl))
            val document = Jsoup.parse(html, episodeUrl)
            val sources = mutableListOf<PlaybackServer>()
            val embeds = html.substringAfter("embeds:{", "").substringBefore("downloads:")
            Regex("([A-Z][A-Z0-9_]*):\\[([^]]*)]").findAll(embeds).forEach { language ->
                Regex("\\{server:\"([^\"\\\\]+)\",url:\"(https://[^\"\\\\\\s]+)\"\\}")
                    .findAll(language.groupValues[2]).forEach { match ->
                        val url = match.groupValues[2]
                        if (isHttpsUrl(url)) sources.add(PlaybackServer("${language.groupValues[1]} · ${match.groupValues[1]}", url))
                    }
            }
            document.select("iframe[src]").forEach { frame ->
                val url = frame.absUrl("src")
                if (isHttpsUrl(url)) sources.add(PlaybackServer("Reproductor principal", url))
            }
            return sources.distinctBy { it.embedUrl }
        }
    }
}
