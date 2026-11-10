package com.animeflix.playback

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Explicit TV/keyboard route: email --Next--> password --Done--> submit. */
@Composable
fun CredentialsForm(email: String, password: String, busy: Boolean, register: Boolean,
    onEmail: (String) -> Unit, onPassword: (String) -> Unit, onSubmit: () -> Unit) {
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val television = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var wasImeVisible by remember { mutableStateOf(false) }
    var lastField by remember { mutableIntStateOf(0) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val canSubmit = !busy && email.isNotBlank() && password.isNotBlank()
    fun edit(field: Int) {
        lastField = field
        (if (field == 0) emailFocus else passwordFocus).requestFocus()
        if (television) scope.launch { withFrameNanos { }; keyboard?.show() }
    }
    fun remoteKey(event: KeyEvent, field: Int, focused: Boolean): Boolean {
        if (!television || imeVisible || !focused || busy) return false
        return when (event.key) {
            Key.DirectionCenter, Key.Enter -> { if (event.type == KeyEventType.KeyDown) edit(field); true }
            Key.DirectionDown -> {
                if (field == 0) { if (event.type == KeyEventType.KeyDown) { lastField = 1; passwordFocus.requestFocus() }; true }
                else if (canSubmit) { if (event.type == KeyEventType.KeyDown) { lastField = 2; submitFocus.requestFocus() }; true } else false
            }
            Key.DirectionUp -> {
                if (field == 1) { if (event.type == KeyEventType.KeyDown) { lastField = 0; emailFocus.requestFocus() }; true } else false
            }
            else -> false
        }
    }
    LaunchedEffect(Unit) { if (television) emailFocus.requestFocus() }
    LaunchedEffect(imeVisible) {
        val closed = wasImeVisible && !imeVisible
        wasImeVisible = imeVisible
        if (television && closed && !busy) {
            // Wait until the IME finishes dispatching Back, then restore the app's key target.
            withFrameNanos { }
            view.requestFocus()
            when (lastField) {
                0 -> emailFocus.requestFocus()
                1 -> passwordFocus.requestFocus()
                else -> if (canSubmit) submitFocus.requestFocus() else passwordFocus.requestFocus()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(email, onEmail, label = { Text("Correo electrónico") }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next, showKeyboardOnFocus = !television),
            keyboardActions = KeyboardActions(onNext = { edit(1) }),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { remoteKey(it, 0, emailFocused) }
                .focusRequester(emailFocus).onFocusChanged { emailFocused = it.isFocused; if (it.isFocused) lastField = 0 }
                .focusProperties { next = passwordFocus; down = passwordFocus })
        OutlinedTextField(password, onPassword, label = { Text("Contraseña") }, singleLine = true, enabled = !busy,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = !busy) { Text(if (visible) "Ocultar" else "Mostrar") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done, showKeyboardOnFocus = !television),
            keyboardActions = KeyboardActions(onDone = {
                if (canSubmit) { lastField = 2; keyboard?.hide(); submitFocus.requestFocus() }
                else if (email.isBlank()) emailFocus.requestFocus()
            }), modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { remoteKey(it, 1, passwordFocused) }
                .focusRequester(passwordFocus).onFocusChanged { passwordFocused = it.isFocused; if (it.isFocused) lastField = 1 }.focusProperties {
                previous = emailFocus; up = emailFocus
                if (canSubmit) { next = submitFocus; down = submitFocus }
            })
        Button(onClick = { keyboard?.hide(); visible = false; onSubmit() }, enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().focusRequester(submitFocus).onFocusChanged { if (it.isFocused) lastField = 2 }
                .focusProperties { previous = passwordFocus; up = passwordFocus }) {
            Text(if (busy) "Conectando…" else if (register) "Crear cuenta" else "Entrar")
        }
        if (television) Text("OK abre el teclado. Siguiente pasa a Contraseña. Listo cierra el teclado; después pulsa Entrar.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
