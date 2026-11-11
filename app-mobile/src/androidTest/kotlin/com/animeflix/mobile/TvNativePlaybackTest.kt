package com.animeflix.mobile

import android.content.Intent
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.playback.PlayerActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the shared TV mode on a physical phone, not a claim of TV hardware validation. */
@RunWith(AndroidJUnit4::class)
class TvNativePlaybackTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun tvMp4UploadKeepsLandscapeAndVideoWhenOpeningMenu() {
        play("https://animeav1.com/media/one-piece/1", null)
    }

    @Test fun tvVoeUsesNativeControlsAndReturnsToServerMenu() {
        play("https://animeav1.com/media/omae-gotoki-ga-maou-ni-kateru-to-omouna-to-yuusha-party-wo-tsuihou-sareta-node-outo-de-kimama-ni-kurashitai/1", "DUB · Voe")
    }

    @Test fun tvBysePointerCanReturnToApplicationControlsWithoutClickingVerification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val url = "https://animeav1.com/media/omae-gotoki-ga-maou-ni-kateru-to-omouna-to-yuusha-party-wo-tsuihou-sareta-node-outo-de-kimama-ni-kurashitai/1"
        ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
            .putExtra("episodeUrl", url).putExtra("title", "Prueba TV Byse")
            .putExtra("tvMode", true).putExtra("nativeMode", true)).use { scenario ->
            rule.waitUntil(60_000) { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("DUB · Byse").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("DUB · Byse").performScrollTo().performClick()
            rule.waitUntil(45_000) { rule.onAllNodesWithText("Este servidor solicita una verificación humana", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Controlar verificación con mando").performClick()
            scenario.onActivity { activity ->
                val web = activity.window.decorView.findFocus() as? android.webkit.WebView
                check(web != null) { "El puntero debe recibir el foco dentro de la WebView" }
                // Move only; the user must perform the human-verification click personally.
                for(key in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK)) {
                    web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                    web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                }
            }
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("Controlar verificación con mando") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Controlar verificación con mando").assertIsFocused()
            rule.onNodeWithText("ANIMEFLIX · REPRODUCTOR").assertExists()
            scenario.onActivity { check(it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) }
        }
    }

    private fun play(url: String, provider: String?) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
            .putExtra("episodeUrl", url).putExtra("title", "Prueba TV")
            .putExtra("tvMode", true).putExtra("nativeMode", true)).use { scenario ->
            rule.waitUntil(60_000) { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) { rule.onAllNodes(hasText("✓", substring = true) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("✓", substring = true)).assertIsFocused()
            if(provider != null) {
                rule.onNodeWithText("$provider").performScrollTo().performClick()
                rule.waitUntil(60_000) {
                    rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() &&
                        rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isEmpty()
                }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isNotEmpty() }
            }
            rule.onNodeWithContentDescription("Vídeo listo").assertExists()
            rule.onNodeWithText("Ver vídeo · cerrar menú").performScrollTo().performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isEmpty() }
            scenario.onActivity {
                check(it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
                it.onBackPressedDispatcher.onBackPressed()
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Vídeo listo").assertExists()
            scenario.onActivity { check(it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) }
        }
    }
}
