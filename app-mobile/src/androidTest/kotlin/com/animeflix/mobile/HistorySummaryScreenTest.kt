package com.animeflix.mobile

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistorySummaryScreenTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun oneChapterPerSeriesIncludingContinueWatchingAfterRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        ActivityScenario.launch<UiReviewActivity>(Intent(context, UiReviewActivity::class.java).putExtra("screen", "history")).use { scenario ->
            fun checkSummary() {
                rule.waitUntil(15_000) { rule.onAllNodesWithText("SEGUIR VIENDO").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Episodio 1").assertDoesNotExist()
                rule.onNodeWithText("Episodio 2").assertDoesNotExist()
                assertEquals(1, rule.onAllNodesWithText("Episodio 3", substring = true).fetchSemanticsNodes().size)
                assertEquals(1, rule.onAllNodesWithText("One Piece").fetchSemanticsNodes().size)
                rule.onNodeWithText("Marcar como visto").assertExists()
            }
            checkSummary()
            scenario.recreate()
            checkSummary()
        }
    }
}
