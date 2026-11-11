package com.animeflix.playback

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.animeflix.data.*
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs the same public embed as playback. Verification gates stop this attempt; never solve them. */
internal class DownloadSourceResolver(private val context: Context) {
    suspend fun resolve(server: PlaybackServer, episode: String): ResolvedVideo {
        if(Mp4UploadResolver.supports(server.embedUrl)) return Mp4UploadResolver().resolve(server)
        require(DownloadOriginPolicy.provider(server) != null)
        return try { web(server, episode) }
        catch(timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw java.io.IOException("El origen no ha preparado un vídeo a tiempo")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun web(server: PlaybackServer, episode: String): ResolvedVideo = withContext(Dispatchers.Main) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) { "Actualiza Android System WebView." }
        var web: WebView? = null
        try {
            withTimeout(45_000) {
                suspendCancellableCoroutine { continuation ->
                    val origins = ProviderPolicy.origins(server.embedUrl)
                    val referers = ConcurrentHashMap<String, String>()
                    val requestOrigins = ConcurrentHashMap<String, String>()
                    val view = WebView(context).also { web = it }
                    fun fail(message: String) { if(continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) }
                    view.settings.apply {
                        javaScriptEnabled = true; domStorageEnabled = true
                        allowFileAccess = false; allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        mediaPlaybackRequiresUserGesture = false; setSupportMultipleWindows(true)
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                    WebViewCompat.addWebMessageListener(view, "AnimeFlixSource", origins) { _, message, origin, _, _ ->
                        if(!continuation.isActive || origin.toString().trimEnd('/') !in origins) return@addWebMessageListener
                        runCatching {
                            val text = message.data.orEmpty()
                            if(text.length > 24_000) return@runCatching
                            val data = JSONObject(text)
                            if(data.optString("kind") == "verification") { fail("Este origen requiere verificación manual."); return@runCatching }
                            val kind = data.optString("kind")
                            if(kind == "status") android.util.Log.i("AnimeFlixDownloadResolver", "${DownloadOriginPolicy.provider(server)}: elements=${data.optInt("elements")}; videos=${data.optInt("videos")}; textLength=${data.optInt("textLength")}")
                            if(kind !in setOf("video", "hls")) return@runCatching
                            val uri = data.getString("url")
                            val referer = data.getString("referer")
                            val duration = data.optDouble("duration")
                            val parent = android.net.Uri.parse(referer)
                            if(!ProviderPolicy.media(uri) || "${parent.scheme}://${parent.host}" !in origins || !duration.isFinite() || duration !in 60.0..86400.0) return@runCatching
                            continuation.resume(ResolvedVideo(uri, referers[uri] ?: referer, view.settings.userAgentString,
                                CookieManager.getInstance().getCookie(uri), if(kind == "hls") "application/x-mpegURL" else "video/mp4", requestOrigins[uri]))
                        }
                    }
                    // A detached preparation view need not render pixels before reporting loaded metadata.
                    WebViewCompat.addDocumentStartJavaScript(view, SOURCE_SCRIPT.replace("v.duration>60 && v.videoWidth>0", "v.duration>60"), origins)
                    view.webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                    }
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.scheme != "https" || request.isForMainFrame
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if(referers.size < 200) request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value?.let { referers[request.url.toString()] = it }
                            if(requestOrigins.size < 200) request.requestHeaders.entries.firstOrNull { it.key.equals("Origin", true) }?.value?.let { requestOrigins[request.url.toString()] = it }
                            return null
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if(request.isForMainFrame) android.util.Log.i("AnimeFlixDownloadResolver", "${DownloadOriginPolicy.provider(server)}: HTTP ${response.statusCode}")
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { fail("El origen no se pudo preparar."); return true }
                    }
                    val src = server.embedUrl.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                    view.layout(0, 0, 1280, 720)
                    view.onResume()
                    view.loadDataWithBaseURL(episode, """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><iframe src="$src" style="width:100%;height:720px;border:0" allow="autoplay; fullscreen" referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>""", "text/html", "UTF-8", null)
                }
            }
        } finally { web?.apply { stopLoading(); onPause(); destroy() } }
    }
}
