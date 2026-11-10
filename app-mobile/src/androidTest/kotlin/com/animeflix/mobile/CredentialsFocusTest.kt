package com.animeflix.mobile

import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsets
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture form only: no accounts, email, real passwords or authentication requests. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CredentialsFocusTest {
    @get:Rule val rule = createAndroidComposeRule<UiReviewActivity>()

    @Test fun nextFromEmailAndDoneFromPasswordReachSubmit() {
        rule.onNodeWithText("Correo electrónico").performTextInput("qa@example.invalid")
        rule.onNodeWithText("Correo electrónico").performImeAction()
        rule.onNodeWithText("Contraseña").assertIsFocused().performTextInput("qa-test-1234")
        rule.onNodeWithText("Contraseña").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithText("Entrar").assertIsFocused().performClick()
        rule.onNodeWithText("Formulario enviado").assertIsDisplayed()
    }

    @Test fun tvBackFromSoftwareKeyboardKeepsRemoteNavigationWorking() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        assumeTrue(rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION)
        rule.onNodeWithText("Correo electrónico").performTextInput("qa@example.invalid")
        rule.onNodeWithText("Correo electrónico").performKeyInput { pressKey(Key.DirectionCenter) }
        waitForKeyboard(true)
        pressSystemBack()
        waitForKeyboard(false)
        rule.waitForIdle()
        rule.onNodeWithText("Correo electrónico").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Contraseña").assertIsFocused().performTextInput("qa-test-1234")
        rule.onNodeWithText("Contraseña").performKeyInput { pressKey(Key.DirectionCenter) }
        waitForKeyboard(true)
        pressSystemBack()
        waitForKeyboard(false)
        rule.waitForIdle()
        rule.onNodeWithText("Contraseña").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Entrar").assertIsFocused()
    }

    private fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
    }
    private fun waitForKeyboard(visible: Boolean) {
        rule.waitUntil(10_000) {
            rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == visible
        }
    }
}
