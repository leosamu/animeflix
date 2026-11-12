package com.animeflix.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AccountManager
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression: selecting Downloads must never fall through to the catalogue branch. No video transfers. */
@RunWith(AndroidJUnit4::class)
class DownloadsNavigationTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun downloadsShowsOnlyLocalLibraryAndCanReturnToExplore(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        AccountManager.create(context).signOut()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(15_000) { runCatching { rule.onAllNodesWithText("Descargas").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            rule.onNodeWithText("Descargas").performClick()
            rule.onNodeWithText("Descargas").assertIsSelected()
            rule.onNodeWithText("Tu biblioteca sin conexión").assertExists()
            rule.onNodeWithText("Buscar anime").assertDoesNotExist()
            rule.onNodeWithText("Catálogo de demostración", substring = true).assertDoesNotExist()
            rule.onNodeWithText("Todavía no tienes animes con capítulos descargados en este perfil.").assertExists()
            rule.onNodeWithText("Explorar").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("Buscar anime").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Explorar").assertIsSelected()
        }
    }
}
