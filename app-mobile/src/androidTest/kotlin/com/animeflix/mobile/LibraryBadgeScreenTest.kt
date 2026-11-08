package com.animeflix.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBadgeScreenTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun libraryBadgeAndDateReactToWatchedChangesAndSurviveRecreation() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val repository = LocalAnimeRepository.create(context)
        val media = "https://animeav1.com/media/qa-library-updates"
        val anime = Anime("qa-library-updates", "Mi serie pendiente", "Prueba local", sourceUrl = "$media/")
        repository.cacheCatalog(listOf(anime)); repository.setFavorite(anime.id,true)
        repository.cacheInventory(EpisodeInventory(media, (1..3).map { Episode("$it", "$media/$it") },1774542234951L,100))
        repository.markWatched("$media/1",true); repository.markWatched("$media/2",false); repository.markWatched("$media/3",false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Mi lista").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Mi lista").performClick()
                rule.waitUntil(15_000) { rule.onAllNodesWithText("2 pendientes").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Último: episodio 3 · 26/03/2026").assertExists()
                repository.markWatched("$media/2",true); repository.markWatched("$media/3",true)
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Al día").fetchSemanticsNodes().isNotEmpty() }
                repository.markWatched("$media/3",false)
                rule.waitUntil(15_000) { rule.onAllNodesWithText("1 pendiente").fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                rule.waitUntil(15_000) { rule.onAllNodesWithText("1 pendiente").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Último: episodio 3 · 26/03/2026").assertExists()
                rule.onNodeWithText("Mi serie pendiente").performClick()
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Ver episodio 3").fetchSemanticsNodes().isNotEmpty() }
                repository.cacheInventory(EpisodeInventory(media, (1..4).map { Episode("$it", "$media/$it") }, null, 200))
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Ver episodio 4").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Último: episodio 4 · Fecha no disponible").assertExists()
            }
        } finally { repository.setFavorite(anime.id,false) }
    }
}
