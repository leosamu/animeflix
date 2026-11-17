package com.animeflix.mobile

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AccountManager
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.data.airingDayLabel
import com.animeflix.data.scheduleDay
import com.animeflix.model.Anime
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class AiringNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun startsOnAiringHidesCaughtUpUntilANewPublishedEpisodeAndKeepsCatalogAlphabetical(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        AccountManager.create(context).signOut()
        val repository = LocalAnimeRepository.create(context)
        val profile = ProfileAnimeRepository.create(context)
        profile.setHideCaughtUp(false)
        val preferences = context.getSharedPreferences("airing_schedule_v1", Context.MODE_PRIVATE)
        val previous = preferences.getString("items", null)
        val oldRecents = context.getSharedPreferences("recent_anime_feed", Context.MODE_PRIVATE)
        val previousRank = oldRecents.getString("ids", null)
        val media = "https://animeav1.com/media/qa-airing-today"
        val today = Anime("av1:qa-airing-today", "QA Programa hoy", "Horario de prueba", sourceUrl = media)
        val yesterday = Anime("av1:qa-airing-yesterday", "QA Programa ayer", "Horario de prueba", sourceUrl = "https://animeav1.com/media/qa-airing-yesterday")
        val alpha = Anime("qa-catalog-alpha", "AAA QA Catálogo", "Prueba de orden")
        val zeta = Anime("qa-catalog-zeta", "ZZZ QA Catálogo", "Prueba de orden")
        repository.cacheCatalog(listOf(today, yesterday, alpha, zeta))
        repository.setFavorite(today.id, true)
        repository.cacheInventory(EpisodeInventory(media, listOf(Episode("1", "$media/1")), null, System.currentTimeMillis()))
        repository.markWatched("$media/1", true); repository.markWatched("$media/2", false)
        repository.recordOpened("$media/1")
        val now = System.currentTimeMillis()
        val yesterdayAt = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        fun cache(latest: String) {
            val records = JSONArray().apply {
                listOf(Triple(today, latest, now), Triple(yesterday, "1", yesterdayAt)).forEach { (anime, number, at) ->
                    put(JSONObject().put("source", anime.sourceUrl).put("title", anime.title).put("synopsis", anime.synopsis)
                        .put("poster", "").put("number", number).put("postedAt", at))
                }
            }
            preferences.edit().putString("items", records.toString()).putLong("updatedAt", now).commit()
        }
        cache("1")
        oldRecents.edit().putString("ids", "${zeta.id}\n${alpha.id}").commit()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(15_000) { rule.onAllNodesWithText(today.title).fetchSemanticsNodes().isNotEmpty() }
                rule.onNode(hasText("En emisión") and hasClickAction()).assertIsSelected()
                rule.onNodeWithText(airingDayLabel(scheduleDay(now), scheduleDay(now))).assertExists()
                rule.onNodeWithContentDescription("Ocultar series al día").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText(today.title).fetchSemanticsNodes().isEmpty() }
                rule.onNodeWithText(yesterday.title).assertExists()
                scenario.recreate()
                rule.waitUntil(15_000) { rule.onAllNodesWithText(yesterday.title).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText(today.title).assertDoesNotExist()
                cache("2")
                rule.waitUntil(10_000) { rule.onAllNodesWithText(today.title).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithContentDescription("Ocultar series al día").assertIsOn()
                rule.onNodeWithText("Nuevo episodio 2").assertExists()
                repository.markWatched("$media/2", true)
                rule.waitUntil(10_000) { rule.onAllNodesWithText(today.title).fetchSemanticsNodes().isEmpty() }
                rule.onNodeWithContentDescription("Ocultar series al día").performClick()
                rule.onNodeWithText(today.title).performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Continuar episodio 1").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, rule.onAllNodesWithText("Continuar episodio 1").fetchSemanticsNodes().size)
                rule.onNodeWithText("Retomar episodio", substring = true).assertDoesNotExist()
                val remove = rule.onNodeWithText("Quitar de mi lista").fetchSemanticsNode().boundsInRoot
                val badge = rule.onNodeWithText("Al día").fetchSemanticsNode().boundsInRoot
                val resume = rule.onNodeWithText("Continuar episodio 1").fetchSemanticsNode().boundsInRoot
                assertTrue(kotlin.math.abs(remove.center.y - badge.center.y) < 10)
                assertTrue(resume.top >= remove.bottom)
                rule.onNodeWithText("Volver").performClick()
                rule.onNode(hasText("Catálogo") and hasClickAction()).performClick()
                rule.onNodeWithText("Buscar anime").performTextInput("QA Catálogo")
                val a = rule.onNodeWithText(alpha.title).fetchSemanticsNode().boundsInRoot
                val z = rule.onNodeWithText(zeta.title).fetchSemanticsNode().boundsInRoot
                assertTrue(a.top < z.top || a.top == z.top && a.left < z.left)
                rule.onNode(hasText("Catálogo") and hasClickAction()).assertIsSelected()
            }
        } finally {
            preferences.edit().putString("items", previous).commit()
            oldRecents.edit().putString("ids", previousRank).commit()
            profile.setHideCaughtUp(false)
            repository.setFavorite(today.id, false)
        }
    }
}
