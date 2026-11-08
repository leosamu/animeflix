package com.animeflix.playback

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.animeflix.data.ResolvedVideo
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Executes the provider's normal embed. No persisted stream URLs or unbounded JS bridge. */
internal object ProviderPolicy {
    fun origins(url: String): Set<String> = when (Uri.parse(url).host) {
        "voe.sx" -> setOf("https://voe.sx", "https://susanexpectcare.com")
        "player.zilla-networks.com" -> setOf("https://player.zilla-networks.com")
        "animeav1.uns.bio" -> setOf("https://animeav1.uns.bio", "https://turboembed.com")
        "byselapuix.com" -> setOf("https://byselapuix.com", "https://n1mwq.org")
        else -> emptySet()
    }
    fun supports(url: String) = url.startsWith("https://") && origins(url).isNotEmpty()
    fun media(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host.orEmpty().lowercase()
        url.length <= 16384 && uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443) &&
            host.contains('.') && !host.endsWith(".local") && !host.endsWith(".localhost") &&
            !host.matches(Regex("[0-9.]+")) && !host.contains(':')
    }.getOrDefault(false)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ProviderVideoResolver(server: PlaybackServer, episodeUrl: String, modifier: Modifier = Modifier,
    onResolved: (ResolvedVideo) -> Unit, onFailure: (String) -> Unit) {
    val resolved by rememberUpdatedState(onResolved)
    val failed by rememberUpdatedState(onFailure)
    var view by remember { mutableStateOf<WebView?>(null) }
    var finished by remember { mutableStateOf(false) }
    var verification by remember { mutableStateOf(false) }
    LaunchedEffect(server.embedUrl, verification) {
        delay(if(verification) 180_000 else 45_000)
        if(!finished) { finished = true; failed("El servidor no ha entregado un vídeo compatible. Reintenta o elige otro servidor.") }
    }
    DisposableEffect(Unit) { onDispose { finished = true; view?.apply { stopLoading(); onPause(); destroy() }; view = null } }
    Column(modifier) {
        Text(if(verification) "Este servidor solicita una verificación humana. Complétala en el recuadro para continuar, o elige otro servidor."
            else "Preparando vídeo de ${server.name}. Si aparece un botón de reproducción, púlsalo.")
        AndroidView(modifier = Modifier.fillMaxWidth().height(220.dp), factory = { context ->
            WebView(context).apply {
                view = this
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                android.util.Log.i("AnimeFlixResolver", "Preparando ${server.name}")
                val origins = ProviderPolicy.origins(server.embedUrl)
                val observedOrigins = ConcurrentHashMap<String, String>()
                val observedReferers = ConcurrentHashMap<String, String>()
                var reportedErrors = 0
                if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                    !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    finished = true
                    failed("Actualiza Android System WebView para preparar el vídeo de este servidor.")
                    return@apply
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportMultipleWindows(true) // No popup WebView is created.
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                WebViewCompat.addWebMessageListener(this, "AnimeFlixSource", origins) { _, message, origin, _, _ ->
                    if(finished || origin.toString().trimEnd('/') !in origins) return@addWebMessageListener
                    runCatching {
                        val raw = message.data.orEmpty()
                        if(raw.length > 24000) return@runCatching
                        val data = JSONObject(raw)
                        if(data.optString("kind") == "verification") { verification = true; return@runCatching }
                        if(data.optString("kind") == "status") {
                            android.util.Log.i("AnimeFlixResolver", "Estado ${server.name}: elementos=${data.optInt("elements")}; vídeos=${data.optInt("videos")}; texto=${data.optInt("textLength")}")
                            return@runCatching
                        }
                        val url = data.getString("url")
                        val referer = data.getString("referer")
                        val kind = data.getString("kind")
                        if(!ProviderPolicy.media(url) || Uri.parse(referer).let { "${it.scheme}://${it.host}" } !in origins)
                            return@runCatching
                        val duration = data.optDouble("duration")
                        if(kind !in setOf("hls", "video") || !duration.isFinite() || duration !in 60.0..86400.0) return@runCatching
                        finished = true
                        android.util.Log.i("AnimeFlixResolver", "Fuente preparada: ${server.name}; tipo=$kind")
                        onPause()
                        resolved(ResolvedVideo(url, observedReferers[url] ?: referer, settings.userAgentString,
                            CookieManager.getInstance().getCookie(url), if(kind == "hls") "application/x-mpegURL" else null,
                            observedOrigins[url]))
                    }
                }
                WebViewCompat.addDocumentStartJavaScript(this, SOURCE_SCRIPT, origins)
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if(message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && reportedErrors++ < 4) {
                            val summary = message.message().replace(Regex("https?://\\S+"), "[URL]")
                                .replace(Regex("[A-Za-z0-9_+/=-]{32,}"), "[token]").take(180)
                            android.util.Log.w("AnimeFlixResolver", "${server.name}: $summary")
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if(reportedErrors++ < 4) android.util.Log.w("AnimeFlixResolver", "${server.name}: error web ${error.errorCode} en ${request.url.host}")
                    }
                    override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if(reportedErrors++ < 4) android.util.Log.w("AnimeFlixResolver", "${server.name}: HTTP ${response.statusCode} en ${request.url.host}")
                    }
                    override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? {
                        // Reuse the Origin sent by the actual browser request, when present.
                        request.requestHeaders.entries.find { it.key.equals("Origin", true) }?.value?.let {
                            if(observedOrigins.size < 200) observedOrigins[request.url.toString()] = it
                        }
                        request.requestHeaders.entries.find { it.key.equals("Referer", true) }?.value?.let {
                            if(observedReferers.size < 200) observedReferers[request.url.toString()] = it
                        }
                        return null
                    }
                    override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean =
                        request.url.scheme != "https" || request.isForMainFrame
                    override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                        if(!finished) { finished = true; failed("El reproductor del servidor se ha cerrado. Prueba otro servidor.") }
                        return true
                    }
                }
                // A single selected iframe, with the original page as its browsing context.
                // Avoid loading the site's initial default server and confusing languages.
                val src = server.embedUrl.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                // Explicit CSS height avoids a zero-height viewport during WebView's first Compose measurement.
                loadDataWithBaseURL(episodeUrl, """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body,iframe{display:block;margin:0;width:100%;height:220px;min-height:220px;border:0;background:#000}</style></head><body><iframe src="$src" allow="autoplay; fullscreen" referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>""", "text/html", "UTF-8", null)
            }
        })
    }
}

// Observe only actual media elements and successful manifest responses; never arbitrary page strings
// (Voe publishes a short sample MP4 that is not the selected episode).
private val SOURCE_SCRIPT = """
    (function(){
      document.addEventListener('play',function(e){if(e.target.tagName==='VIDEO')e.target.muted=true;},true);
      if(location.hostname==='byselapuix.com'||location.hostname==='n1mwq.org') {
        document.addEventListener('DOMContentLoaded',function(){
          var style=document.createElement('style');
          style.textContent='html,body,#root,.video-embed-page,.video-page__player,.video-page__player-frame,.jw8-player-shell{height:100%!important;min-height:100%!important;width:100%!important}.jw8-player-shell iframe{height:100%!important;width:100%!important}';
          document.head.appendChild(style);
        });
      }
      setTimeout(function(){try{AnimeFlixSource.postMessage(JSON.stringify({kind:'status',
        elements:document.querySelectorAll('*').length,videos:document.querySelectorAll('video').length,
        textLength:document.body?document.body.innerText.length:0}));}catch(e){}},8000);
      function send(url,kind,duration){
        try { if(typeof url==='string' && url.indexOf('https://')===0)
          AnimeFlixSource.postMessage(JSON.stringify({url:url,kind:kind,duration:duration||0,referer:location.href})); } catch(e){}
      }
      var masters={};
      function inspect(url,text){
        if(typeof text!=='string'||text.trim().indexOf('#EXTM3U')!==0)return;
        if(text.indexOf('#EXT-X-STREAM-INF:')>=0){
          text.split(/\r?\n/).forEach(function(line){
            var child=line.trim(),m=/#EXT-X-MEDIA:.*URI="([^"]+)"/.exec(child);
            if(m)child=m[1];
            if(child&&child[0]!=='#'){try{masters[new URL(child,url).href]=url;}catch(e){}}
          });
        }
        var total=0,pattern=/^#EXTINF:([0-9.]+)/gm,match;
        while((match=pattern.exec(text))!==null)total+=parseFloat(match[1]);
        // Short sample/ad playlists are not episodes. Use the parent master to retain audio/quality tracks.
        if(total>60)send(masters[url]||url,'hls',total);
      }
      var originalFetch=window.fetch;
      if(originalFetch)window.fetch=function(){return originalFetch.apply(this,arguments).then(function(r){
        var type=r.headers.get('content-type')||'';
        if(r.ok && (/mpegurl/i.test(type)||/\.m3u8(?:\?|$)/i.test(r.url)))
          r.clone().text().then(function(t){inspect(r.url,t);}).catch(function(){});
        return r;
      });};
      var open=XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open=function(){
        this.addEventListener('load',function(){try{
          if(this.status>=200&&this.status<300&&(this.responseType===''||this.responseType==='text'))
            inspect(this.responseURL,this.responseText);
        }catch(e){}});
        return open.apply(this,arguments);
      };
      setInterval(function(){
        // Report verification gates for the user; never click or solve them automatically.
        if(document.querySelector('.captcha-gate')) {
          try{AnimeFlixSource.postMessage(JSON.stringify({kind:'verification'}));}catch(e){}
          return;
        }
        document.querySelectorAll('video').forEach(function(v){
          if(v.duration>60 && v.videoWidth>0)send(v.currentSrc,'video',v.duration);
          if(v.paused){v.muted=true;v.play().catch(function(){});}
        });
        ['.jw-icon-display','.vjs-big-play-button','button[aria-label="Play"]','.player-button'].forEach(function(s){
          var p=document.querySelector(s);if(p)p.click();
        });
      },1200);
    })();
""".trimIndent()
