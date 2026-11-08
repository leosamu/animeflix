package com.animeflix.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.Anime
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun historyAndWatchedStatusSurviveActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        runBlocking {
            val repository = LocalAnimeRepository.create(context)
            repository.cacheCatalog(listOf(Anime("qa-history", "Prueba historial", "Descripción",
                sourceUrl = "https://animeav1.com/media/qa-history")))
            repository.recordOpened("https://animeav1.com/media/qa-history/3")
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Prueba historial").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Historial").performClick()
        rule.onNodeWithText("Prueba historial").assertIsDisplayed()
        rule.onNodeWithText("Episodio 3").assertIsDisplayed()
        rule.onNodeWithText("Marcar como visto").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Visto · desmarcar").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Visto · desmarcar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Visto · desmarcar").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Marcar como visto").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Marcar como visto").assertExists()
    }
}
