package com.animeflix.mobile

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AccountManager
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.Anime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentCatalogScreenTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun cachedPublicationOrderSurvivesRecreationWhileFavoritesRemainAlphabetical(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        AccountManager.create(context).signOut()
        val preferences = context.getSharedPreferences("recent_anime_feed", Context.MODE_PRIVATE)
        val previous = preferences.getString("ids", null)
        preferences.edit().putString("ids", "av1:qa-order-zeta\nav1:qa-order-alpha").commit()
        val repository = LocalAnimeRepository.create(context)
        val alpha = Anime("av1:qa-order-alpha", "QA Orden Alpha", "Prueba local")
        val zeta = Anime("av1:qa-order-zeta", "QA Orden Zeta", "Prueba local")
        repository.cacheCatalog(listOf(alpha, zeta))
        repository.setFavorite(alpha.id, true)
        repository.setFavorite(zeta.id, true)
        context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE).edit().putBoolean("hideCaughtUp", false).commit()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(15_000) { rule.onAllNodesWithText("Buscar anime").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Buscar anime").performTextInput("QA Orden")
                assertBefore(zeta.title, alpha.title)
                scenario.recreate()
                rule.waitUntil(15_000) { rule.onAllNodesWithText(zeta.title).fetchSemanticsNodes().isNotEmpty() }
                assertBefore(zeta.title, alpha.title)
                rule.onNodeWithText("Mi lista").performClick()
                assertBefore(alpha.title, zeta.title)
            }
        } finally {
            preferences.edit().putString("ids", previous).commit()
            repository.setFavorite(alpha.id, false)
            repository.setFavorite(zeta.id, false)
        }
    }

    private fun assertBefore(first: String, second: String) {
        val a = rule.onNodeWithText(first).fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithText(second).fetchSemanticsNode().boundsInRoot
        assertTrue("$first debe preceder a $second", a.top < b.top || (a.top == b.top && a.left < b.left))
    }
}
