package com.animeflix.mobile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun awaitCatalog() {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Catálogo") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Catálogo") and hasClickAction()).performClick()
        rule.onNodeWithText("Buscar anime").performTextClearance()
        rule.onNodeWithText("Buscar anime").performTextInput("Viaje estelar")
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Viaje estelar").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchAndEmptyStateSurviveRecreation() {
        awaitCatalog()
        rule.onNodeWithText("Buscar anime").performTextInput("zzzz-no-result")
        rule.onNodeWithText("Sin resultados").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sin resultados").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Borrar").performClick()
        rule.onNodeWithText("Buscar anime").performTextInput("Viaje estelar")
        rule.onNode(hasText("Viaje estelar") and !hasSetTextAction()).assertIsDisplayed()
    }

    @Test fun detailSurvivesRecreationAndReturnsToCatalog() {
        awaitCatalog()
        rule.onNode(hasText("Viaje estelar") and !hasSetTextAction()).performClick()
        rule.onNodeWithText("Sinopsis").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sinopsis").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Volver").performClick()
        rule.onNode(hasText("Catálogo") and hasClickAction()).assertIsSelected()
        rule.onNodeWithText("Mi lista").performClick()
        rule.onNodeWithText("Tus favoritos, siempre a mano.").assertIsDisplayed()
    }
}
