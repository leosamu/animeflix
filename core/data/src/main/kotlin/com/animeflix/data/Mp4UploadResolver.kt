package com.animeflix.data

import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URI

data class ResolvedVideo(val url: String, val referer: String, val userAgent: String = Mp4UploadResolver.USER_AGENT,
    val cookie: String? = null, val mimeType: String? = null, val origin: String? = null)

/** Reads the MP4 configuration published in the provider's embed page. No DRM or challenge bypass. */
class Mp4UploadResolver {
    suspend fun resolve(server: PlaybackServer): ResolvedVideo = withContext(Dispatchers.IO) {
        require(supports(server.embedUrl))
        val response = Jsoup.connect(server.embedUrl).userAgent(USER_AGENT).timeout(20_000).maxBodySize(1_000_000).execute()
        val url = parse(response.body()) ?: throw IOException("El servidor no publica un vídeo MP4 compatible")
        ResolvedVideo(url, server.embedUrl)
    }

    companion object {
        const val USER_AGENT = "AnimeFlix/0.6 (Android native player)"
        fun supports(url: String): Boolean = Regex("https://(?:www\\.)?mp4upload\\.com/embed-[a-zA-Z0-9]+\\.html").matches(url)
        internal fun parse(html: String): String? {
            val match = Regex("""player\.src\(\s*\{[\s\S]*?\bsrc\s*:\s*["']([^"']+)["']""").find(html) ?: return null
            val value = Parser.unescapeEntities(match.groupValues[1].replace("\\/", "/"), false)
            return value.takeIf { candidate -> runCatching {
                val uri = URI(candidate)
                candidate.length <= 4096 && uri.scheme == "https" && uri.userInfo == null &&
                    (uri.host == "mp4upload.com" || uri.host?.endsWith(".mp4upload.com") == true) &&
                    uri.port in listOf(-1, 443, 183) && uri.path.lowercase().endsWith(".mp4")
            }.getOrDefault(false) }
        }
    }
}
