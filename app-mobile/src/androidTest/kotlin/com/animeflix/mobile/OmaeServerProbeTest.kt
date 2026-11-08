package com.animeflix.mobile

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.animeflix.data.AnimeAv1Episodes
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Diagnostic matrix, not an assertion that every external provider is available. */
@RunWith(AndroidJUnit4::class)
class OmaeServerProbeTest {
    private data class Candidate(val url: String, val referer: String, val agent: String)

    @SuppressLint("SetJavaScriptEnabled")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun probePublishedSubAndDubServers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val page = "https://animeav1.com/media/omae-gotoki-ga-maou-ni-kateru-to-omouna-to-yuusha-party-wo-tsuihou-sareta-node-outo-de-kimama-ni-kurashitai/1"
        val published = AnimeAv1Episodes().servers(page).filter { !it.name.contains("MP4Upload") }
        val filter = InstrumentationRegistry.getArguments().getString("probeServer")
        val holdMs = (InstrumentationRegistry.getArguments().getString("probeHoldSeconds")?.toLongOrNull() ?: 0).coerceIn(0, 60) * 1000
        val servers = published.filter { filter == null || it.name == filter }
        assertEquals("Servidores publicados esperados", if(filter == null) 8 else 1, servers.size)
        if(holdMs > 0) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        val origins = setOf("https://player.zilla-networks.com", "https://animeav1.uns.bio", "https://turboembed.com",
            "https://voe.sx", "https://susanexpectcare.com", "https://byselapuix.com", "https://n1mwq.org")
        for(server in servers) {
            val expectedHosts = when {
                server.name.contains("UPNShare") -> setOf("animeav1.uns.bio", "turboembed.com")
                server.name.contains("Voe") -> setOf("voe.sx", "susanexpectcare.com")
                server.name.contains("Byse") -> setOf("byselapuix.com", "n1mwq.org")
                else -> setOf("player.zilla-networks.com")
            }
            val candidates = CopyOnWriteArrayList<Candidate>()
            val errors = CopyOnWriteArrayList<String>()
            val played = AtomicReference<JSONObject?>(null)
            val ready = CountDownLatch(1)
            val rootLoaded = AtomicBoolean(false)
            val result = JSONObject().put("server", server.name).put("episode", 1).put("probeVersion",3)
            var web: WebView? = null
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        web = WebView(activity).apply {
                            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                            settings.allowFileAccess = false; settings.allowContentAccess = false
                            settings.mediaPlaybackRequiresUserGesture = false // QA-only autoplay probe.
                            settings.setSupportMultipleWindows(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                WebViewCompat.addWebMessageListener(this, "AnimeFlixProbe", origins) { _, message, origin, _, _ ->
                                    if(!rootLoaded.get() || origin.host !in expectedHosts) return@addWebMessageListener
                                    runCatching {
                                        val data = JSONObject(message.data.orEmpty())
                                        if(data.optDouble("duration") > 60 && data.optDouble("time") > 1 && data.optInt("width") > 0) {
                                            played.set(data); ready.countDown()
                                        }
                                    }
                                }
                                WebViewCompat.addDocumentStartJavaScript(this, """
                                    (function(){
                                      setInterval(function(){
                                        document.querySelectorAll('video').forEach(function(v){
                                          if(v.duration>60){
                                            if(v.paused)v.play().catch(function(){});
                                            if(!v.paused&&v.currentTime>1&&v.videoWidth>0)
                                              AnimeFlixProbe.postMessage(JSON.stringify({duration:v.duration,time:v.currentTime,width:v.videoWidth}));
                                          }
                                        });
                                        ['.jw-icon-display','.vjs-big-play-button','button[aria-label="Play"]'].forEach(function(s){
                                          var p=document.querySelector(s);if(p)p.click();
                                        });
                                      },1000);
                                    })();
                                """.trimIndent(), origins)
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    if(message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && errors.size < 5)
                                        errors.add(message.message().replace(Regex("https?://\\S+"), "[URL]").take(140))
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    errors.add("El proceso web terminó (crash=${detail.didCrash()})")
                                    ready.countDown()
                                    return true
                                }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.url.scheme != "https" || (request.isForMainFrame && request.url.host != "animeav1.com")
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    // The original page initially embeds DUB/HLS; do not attribute it to another selected server/language.
                                    val requested = request.url.toString().substringBefore('#')
                                    val isPublishedEmbed = published.any { it.embedUrl.substringBefore('#') == requested }
                                    if(!request.isForMainFrame && isPublishedEmbed && requested != server.embedUrl.substringBefore('#'))
                                        return WebResourceResponse("text/html", "UTF-8", "<html></html>".byteInputStream())
                                    val path = request.url.path.orEmpty().lowercase()
                                    val referer = request.requestHeaders.entries.find { it.key.equals("Referer",true) }?.value.orEmpty()
                                    if(rootLoaded.get() && android.net.Uri.parse(referer).host in expectedHosts && request.url.scheme == "https" &&
                                        (path.endsWith(".m3u8") || path.endsWith(".mp4")) && candidates.none { it.url == request.url.toString() }) {
                                        candidates.add(Candidate(request.url.toString(), request.requestHeaders.entries.find { it.key.equals("Referer",true) }?.value ?: server.embedUrl,
                                            request.requestHeaders.entries.find { it.key.equals("User-Agent",true) }?.value ?: "AnimeFlix QA"))
                                        ready.countDown()
                                    }
                                    return null
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    if(request.url.host in origins.map { android.net.Uri.parse(it).host } && errors.size < 5) errors.add("HTTP ${response.statusCode} (${request.url.host})")
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    if(url == page && rootLoaded.compareAndSet(false,true)) view.evaluateJavascript(
                                        "(function(){var f=document.querySelector('iframe[title=\"Episodio Embebido\"]');if(f)f.src=${JSONObject.quote(server.embedUrl)};})();", null)
                                }
                            }
                            activity.setContentView(this)
                            loadUrl(page)
                        }
                    }
                    ready.await(25, TimeUnit.SECONDS)
                    if(holdMs > 0) Thread.sleep(holdMs)
                    result.put("webPlaybackObserved", played.get() != null)
                    played.get()?.let { result.put("webVideo",it) }
                    val options = candidates.sortedBy { if(it.url.contains(".m3u8")) 0 else 1 }.take(2)
                    result.put("mediaRequests", candidates.size)
                    val nativeResults = org.json.JSONArray()
                    for(candidate in options) {
                        val done = CountDownLatch(1)
                        val probe = JSONObject().put("host", android.net.Uri.parse(candidate.url).host)
                        var native: ExoPlayer? = null
                        scenario.onActivity { activity ->
                            val factory = DefaultHttpDataSource.Factory().setUserAgent(candidate.agent)
                                .setDefaultRequestProperties(mapOf("Referer" to candidate.referer)).setConnectTimeoutMs(10_000).setReadTimeoutMs(10_000)
                            native = ExoPlayer.Builder(activity).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                                volume = 0f
                                addListener(object : Player.Listener {
                                    override fun onRenderedFirstFrame() { probe.put("firstFrame",true); probe.put("durationMs",duration); done.countDown() }
                                    override fun onPlayerError(error: PlaybackException) { probe.put("error",error.errorCodeName); done.countDown() }
                                })
                                setMediaItem(MediaItem.fromUri(candidate.url)); prepare(); playWhenReady = true
                            }
                            web?.onPause()
                            activity.setContentView(PlayerView(activity).apply { player = native })
                        }
                        done.await(20, TimeUnit.SECONDS)
                        scenario.onActivity {
                            probe.put("durationMs", native?.duration ?: -1)
                            probe.put("nativeAnimeVideo", probe.optBoolean("firstFrame") && (native?.duration ?: 0) > 60_000)
                            native?.release()
                        }
                        nativeResults.put(probe)
                        if(probe.optBoolean("nativeAnimeVideo")) break
                    }
                    result.put("native",nativeResults)
                    result.put("errors", org.json.JSONArray(errors))
                } catch(failure: Exception) { result.put("probeError",failure.javaClass.simpleName) }
                finally { scenario.onActivity { web?.stopLoading(); web?.destroy() } }
            }
            Log.i("AnimeFlixServerProbe", result.toString())
        }
    }
}
