package com.animeflix.data

import com.animeflix.model.PlaybackServer
import java.net.URI

/** Rotation is by provider, never by language or a different version of the episode. */
object DownloadOriginPolicy {
    val providers = listOf("MP4Upload", "Voe", "UPNShare")
    fun provider(server: PlaybackServer): String? = runCatching {
        val uri = URI(server.embedUrl)
        if(uri.scheme != "https" || uri.userInfo != null || uri.port != -1 && uri.port != 443) return null
        when(uri.host?.lowercase()) {
            "mp4upload.com", "www.mp4upload.com" -> if(Mp4UploadResolver.supports(server.embedUrl)) "MP4Upload" else null
            "voe.sx" -> "Voe"
            "animeav1.uns.bio" -> "UPNShare"
            else -> null
        }
    }.getOrNull()
    fun language(server: PlaybackServer): String? = server.name.substringBefore(" · ", "").trim().uppercase().ifEmpty { null }
    fun shouldRotateSlowSource(elapsedMs: Long, total: Long, bytes: Long, percent: Float, hasAlternative: Boolean): Boolean {
        if(!hasAlternative || elapsedMs < 60_000) return false
        val remaining = if(total > 0 && bytes > 0) elapsedMs.toDouble() * (total - bytes) / bytes
            else if(percent.isFinite() && percent > 0) elapsedMs.toDouble() * (100 - percent) / percent else 0.0
        return remaining > 20 * 60_000
    }
    fun candidates(servers: List<PlaybackServer>, selected: PlaybackServer, last: String?, cooling: Set<String> = emptySet()): List<PlaybackServer> {
        val selectedLanguage = language(selected)
        val compatible = servers.filter { provider(it) != null && (if(selectedLanguage == null) it.embedUrl == selected.embedUrl else language(it) == selectedLanguage) }
        val start = (providers.indexOf(last) + 1).mod(providers.size)
        val order = (providers.indices).map { providers[(start + it) % providers.size] }
        return compatible.distinctBy { provider(it) }.sortedBy { order.indexOf(provider(it)) }
            .filter { provider(it) !in cooling }
    }
}
