package com.animeflix.mobile

import com.animeflix.playback.PlayerActivity

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OriginalEpisodeWebViewTest {
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun originalPageLoadsItsPublishedIframe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val url = "https://animeav1.com/media/one-piece/1"
        ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
            .putExtra("episodeUrl", url).putExtra("title", "Episodio 1").putExtra("nativeMode", false)).use { scenario ->
            var frame = ""
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline && !frame.contains("https://")) {
                val ready = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val web = findWebView(activity.window.decorView)
                    if (web == null) ready.countDown()
                    else web.evaluateJavascript("document.querySelector('iframe') ? document.querySelector('iframe').src : ''") {
                        frame = it.orEmpty(); ready.countDown()
                    }
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                if (!frame.contains("https://")) Thread.sleep(300)
            }
            assertTrue("No se cargó el iframe publicado en la página original", frame.contains("https://"))
        }
    }
}
