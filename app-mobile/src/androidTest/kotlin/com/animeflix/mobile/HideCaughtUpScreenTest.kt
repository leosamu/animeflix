package com.animeflix.mobile

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HideCaughtUpScreenTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun hidesOnlyKnownCaughtUpTitlesPersistsAndRestoresNewEpisodes() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val preferences = context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("hideCaughtUp",false).commit()
        val repository = LocalAnimeRepository.create(context)
        val base = "https://animeav1.com/media/qa-hide-done"
        val done = Anime("qa-hide-done","ZZ QA Serie al día","Prueba local",sourceUrl=base)
        val unknown = Anime("qa-hide-unknown","ZZ QA Serie sin comprobar","Prueba local",sourceUrl="https://animeav1.com/media/qa-hide-unknown")
        val original = repository.observeCatalog().first().filter { it.favorite && it.id !in setOf(done.id,unknown.id) }.map { it.id }
        repository.observeCatalog().first().filter { it.favorite }.forEach { repository.setFavorite(it.id,false) }
        repository.cacheCatalog(listOf(done,unknown))
        repository.setFavorite(done.id,true); repository.setFavorite(unknown.id,true)
        repository.cacheInventory(EpisodeInventory(base,listOf(Episode("1","$base/1")),null,100))
        repository.markWatched("$base/1",true); repository.markWatched("$base/2",false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Mi lista").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Mi lista").performClick()
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Al día").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText(done.title).assertExists()
                rule.onNodeWithContentDescription("Ocultar series al día").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText(done.title).fetchSemanticsNodes().isEmpty() }
                rule.onNodeWithText(unknown.title).assertExists()
                rule.onNodeWithContentDescription("Ocultar series al día").assertIsOn()
                assertTrue(preferences.getBoolean("hideCaughtUp",false))
                scenario.recreate()
                rule.waitUntil(15_000) { rule.onAllNodesWithText(unknown.title).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithContentDescription("Ocultar series al día").assertIsOn()
                rule.onNodeWithText(done.title).assertDoesNotExist()
                repository.cacheInventory(EpisodeInventory(base,(1..2).map { Episode("$it","$base/$it") },null,200))
                rule.waitUntil(15_000) { rule.onAllNodesWithText(done.title).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithContentDescription("Ocultar series al día").assertIsOn()
                rule.onNodeWithText("1 pendiente").assertExists()
            }
        } finally {
            preferences.edit().putBoolean("hideCaughtUp",false).commit()
            repository.setFavorite(done.id,false); repository.setFavorite(unknown.id,false)
            original.forEach { repository.setFavorite(it,true) }
        }
    }
}
