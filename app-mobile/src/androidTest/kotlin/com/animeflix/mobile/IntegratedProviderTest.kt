package com.animeflix.mobile

import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.playback.PlayerActivity
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the actual production screen/resolver, using only the separate QA package. */
@RunWith(AndroidJUnit4::class)
class IntegratedProviderTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun voeDub() = probe("DUB · Voe", required = true)
    @Test fun voeSub() = probe("SUB · Voe", required = true)
    @Test fun hlsDub() = probe("DUB · HLS")
    @Test fun hlsSub() = probe("SUB · HLS")
    @Test fun upnDub() = probe("DUB · UPNShare", required = true)
    @Test fun upnSub() = probe("SUB · UPNShare", required = true)
    @Test fun byseDub() = probe("DUB · Byse")
    @Test fun byseSub() = probe("SUB · Byse")

    private fun probe(server: String, required: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        val url = "https://animeav1.com/media/omae-gotoki-ga-maou-ni-kateru-to-omouna-to-yuusha-party-wo-tsuihou-sareta-node-outo-de-kimama-ni-kurashitai/1"
        ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
            .putExtra("episodeUrl", url).putExtra("title", "Prueba de servidores").putExtra("nativeMode", true)).use { scenario ->
            rule.waitUntil(30_000) { rule.onAllNodesWithText(server).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(server).performScrollTo().performClick()
            rule.waitForIdle()
            var ready = false
            var verification = false
            try {
                rule.waitUntil(60_000) {
                    ready = rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty()
                    verification = rule.onAllNodesWithText("Este servidor solicita una verificación humana", substring = true).fetchSemanticsNodes().isNotEmpty()
                    ready || verification || rule.onAllNodesWithText("El servidor no ha entregado", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                        rule.onAllNodesWithText("No se pudo reproducir", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                        rule.onAllNodesWithText("Este servidor ha rechazado", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            } catch (_: androidx.compose.ui.test.ComposeTimeoutException) { /* An unavailable provider is a diagnostic result. */ }
            Log.i("AnimeFlixIntegratedProbe", JSONObject().put("server", server).put("nativeFirstFrame", ready).put("manualVerification",verification).toString())
            if(server.contains("Byse")) check(ready || verification) { "Byse debe entregar vídeo o mostrar su verificación manual" }
            val hold = (InstrumentationRegistry.getArguments().getString("reviewHoldSeconds")?.toLongOrNull() ?: 0).coerceIn(0,30)
            if(verification && hold > 0) Thread.sleep(hold * 1000)
            if(required) check(ready) { "$server no ha producido el primer fotograma nativo" }
            if(ready) {
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Continuar en", substring = true).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Ampliar").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isEmpty() }
                rule.onNodeWithContentDescription("Vídeo listo").assertExists()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                rule.waitUntil(10_000) {
                    var portrait = false
                    scenario.onActivity { portrait = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                    portrait && rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription("Vídeo listo").assertExists()
            }
        }
    }
}
