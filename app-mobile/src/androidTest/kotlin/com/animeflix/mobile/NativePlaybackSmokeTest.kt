package com.animeflix.mobile

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.playback.PlayerActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real network/decoder test, isolated in QA; depends on the provider remaining available. */
@RunWith(AndroidJUnit4::class)
class NativePlaybackSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun nativeMp4UploadRendersVideoAndSavesProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
            .putExtra("episodeUrl", "https://animeav1.com/media/one-piece/1")
            .putExtra("title", "Episodio 1").putExtra("nativeMode", true)).use {
            rule.waitUntil(60_000) { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(20_000) { rule.onAllNodesWithText("Continuar en", substring = true).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Ampliar").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithContentDescription("Vídeo listo").assertExists()
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitUntil(10_000) { rule.onAllNodesWithText("ANIMEFLIX · REPRODUCTOR").fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10_000) {
                var portrait = false
                it.onActivity { activity -> portrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                portrait
            }
            rule.onNodeWithContentDescription("Vídeo listo").assertExists()
        }
    }
}
