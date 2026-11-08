package com.animeflix.mobile

import com.animeflix.playback.WebPlaybackProgress

import android.webkit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animeflix.model.WatchEntry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class WebPlaybackProgressTest {
    @Test fun restoresAndReportsProgressFromAllowedOriginWithoutRemoteNetwork() {
        val saved = AtomicLong(-1)
        val received = CountDownLatch(1)
        var installed = false
        var view: WebView? = null
        val episode = "https://animeav1.com/media/example/1"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    view = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse =
                                WebResourceResponse("text/html", "UTF-8", """
                                    <html><body><video id="v"></video><script>
                                    var v=document.getElementById('v'), position=0;
                                    Object.defineProperty(v,'duration',{get:function(){return 120;}});
                                    Object.defineProperty(v,'currentTime',{get:function(){return position;},set:function(p){position=p;}});
                                    v.dispatchEvent(new Event('loadedmetadata'));
                                    setTimeout(function(){v.dispatchEvent(new Event('playing'));v.dispatchEvent(new Event('pause'));},500);
                                    </script></body></html>
                                """.trimIndent().byteInputStream())
                        }
                        installed = WebPlaybackProgress(this, episode,
                            { WatchEntry(episode, episode.substringBeforeLast('/'), "1", positionMs = 30_000, durationMs = 120_000) },
                            { _, position, _ -> saved.set(position); received.countDown() }).install()
                        if (installed) loadUrl("https://player.zilla-networks.com/animeflix-test")
                    }
                }
                assumeTrue("Android System WebView no admite observación de progreso", installed)
                assertTrue("No llegó progreso desde el vídeo de prueba", received.await(15, TimeUnit.SECONDS))
                assertEquals(30_000L, saved.get())
            } finally { scenario.onActivity { view?.destroy() } }
        }
    }
}
