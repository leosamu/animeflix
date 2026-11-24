package com.animeflix.mobile

import android.net.Uri
import android.webkit.WebResourceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mlm.adblock.AdblockEngine
import org.mlm.adblock.RequestTypeMapper
import java.net.URL

internal enum class AdMode(val label: String) {
    Original("Motor original"),
    Brave("Brave (experimental)"),
    Off("Desactivado")
}

internal object BraveBlocker {
    private val loadLock = Mutex()
    private val filterUrls = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt"
    )
    @Volatile private var engine: AdblockEngine? = null

    suspend fun load(): Boolean = loadLock.withLock {
        if (engine != null) return@withLock true
        withContext(Dispatchers.IO) {
            try {
                val rules = filterUrls.joinToString("\n") { url ->
                    val connection = URL(url).openConnection().apply {
                        connectTimeout = 12_000
                        readTimeout = 12_000
                    }
                    connection.getInputStream().bufferedReader().use { reader ->
                        val buffer = CharArray(4_096)
                        val result = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            check(result.length + count <= 8_000_000) { "Lista de filtros demasiado grande" }
                            result.append(buffer, 0, count)
                        }
                        result.toString()
                    }
                }
                val candidate = AdblockEngine.create()
                try {
                    if (candidate.loadFilterList(rules)) {
                        engine = candidate
                        true
                    } else {
                        candidate.close()
                        false
                    }
                } catch (failure: Exception) {
                    candidate.close()
                    throw failure
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            } catch (_: LinkageError) {
                false
            }
        }
    }

    fun blocks(request: WebResourceRequest, episodeUrl: String): Boolean {
        val headers = request.requestHeaders
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val source = header("Referer")?.takeIf { Uri.parse(it).scheme == "https" } ?: episodeUrl
        val type = when (header("Sec-Fetch-Dest")) {
            "frame", "iframe" -> "subdocument"
            "script" -> "script"
            "image" -> "image"
            "style" -> "stylesheet"
            "font" -> "font"
            "audio", "video" -> "media"
            else -> RequestTypeMapper.from(request.url, header("Accept").orEmpty())
        }
        return engine?.shouldBlock(request.url.toString(), source, type) == true
    }

    fun cosmeticScript(url: String): String? {
        val css = engine?.cosmeticResources(url)?.css()?.takeIf(String::isNotBlank) ?: return null
        return """
            (() => {
              const style = document.createElement('style');
              style.textContent = ${JSONObject.quote(css)};
              const parent = document.head || document.documentElement;
              if (parent) parent.append(style);
              else document.addEventListener('DOMContentLoaded', () => document.head.append(style), { once: true });
            })();
        """.trimIndent()
    }
}
