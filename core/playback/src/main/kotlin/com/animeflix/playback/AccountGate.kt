package com.animeflix.playback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animeflix.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalProfileViewModelKey = staticCompositionLocalOf { "legacy" }

@Composable
inline fun <reified T : ViewModel> profileViewModel(): T = viewModel(key = "${LocalProfileViewModelKey.current}:${T::class.java.name}")

/** Shared account/profile entry for phone and TV. QA retains its deterministic, isolated catalog. */
@Composable
fun AccountGate(enabled: Boolean = true, allowPreview: Boolean = false, authCallback: String? = null,
    onAuthCallbackConsumed: () -> Unit = {}, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val context = LocalContext.current
    val accounts = remember { AccountManager.create(context) }
    val account by accounts.state.collectAsStateWithLifecycle()
    val recovery by accounts.recovery.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null; notice = null
            try { action() }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = if (failure is CloudException || failure is IllegalArgumentException) failure.message else "No se pudo conectar. Comprueba tu conexión y vuelve a intentarlo."
            } finally { busy = false }
        }
    }
    LaunchedEffect(authCallback) {
        if (authCallback != null) {
            busy = true; error = null; notice = null
            try {
                if (!accounts.openAuthCallback(authCallback)) notice = "Correo confirmado. Ya puedes iniciar sesión."
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = if (failure is CloudException || failure is IllegalArgumentException) failure.message else "No se pudo abrir el enlace. Solicita otro correo e inténtalo de nuevo."
            } finally { onAuthCallbackConsumed(); busy = false }
        }
    }
    LaunchedEffect(account.accountId) {
        if (account.accountId != null && !account.preview) {
            try { accounts.refreshProfiles() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = "No se pudieron actualizar los perfiles. Puedes reintentar o abrir un perfil ya guardado." }
        }
    }
    MaterialTheme(colorScheme = AnimeFlixColors) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when {
                recovery != null -> {
                    var newPassword by remember { mutableStateOf("") }
                    var repeated by remember { mutableStateOf("") }
                    var visible by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Nueva contraseña", style = MaterialTheme.typography.headlineLarge)
                        Text("Cuenta: ${recovery?.email}")
                        Text("Elige una contraseña de al menos 8 caracteres.")
                        OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Nueva contraseña") }, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(repeated, { repeated = it }, label = { Text("Repetir contraseña") }, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), enabled = !busy, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar contraseñas" else "Mostrar contraseñas") }
                        Button(onClick = { run {
                            accounts.updateRecoveryPassword(newPassword, repeated)
                            newPassword = ""; repeated = ""
                            notice = "Contraseña cambiada. Inicia sesión con la nueva contraseña."
                        } }, enabled = !busy && newPassword.length >= 8 && repeated.isNotEmpty()) { Text(if (busy) "Guardando…" else "Guardar contraseña") }
                        TextButton(onClick = { run { accounts.cancelPasswordReset() } }, enabled = !busy) { Text("Cancelar") }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                account.accountId == null -> {
                    var email by rememberSaveable { mutableStateOf("") }
                    // Password is deliberately not saveable across activity/process recreation.
                    var password by remember { mutableStateOf("") }
                    var register by rememberSaveable { mutableStateOf(false) }
                    var importLink by remember { mutableStateOf(false) }
                    var recoveryLink by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(24.dp))
                        Text("ANIMEFLIX", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text("Una cuenta, una aventura para cada perfil.")
                        if (accounts.config.configured) {
                            Text(if (register) "Crear cuenta" else "Iniciar sesión", style = MaterialTheme.typography.titleLarge)
                            CredentialsForm(email, password, busy, register, onEmail = { email = it }, onPassword = { password = it }, onSubmit = { run {
                                if (register) {
                                    if (accounts.signUp(email, password)) {
                                        register = false
                                        notice = "Revisa tu correo y confirma la cuenta. Después podrás iniciar sesión."
                                    }
                                } else accounts.signIn(email, password)
                                password = ""
                            } })
                            TextButton(onClick = { register = !register; error = null }, enabled = !busy) { Text(if (register) "Ya tengo cuenta" else "Crear una cuenta") }
                            if (!register) {
                                TextButton(onClick = { run {
                                    accounts.requestPasswordReset(email)
                                    notice = "Si existe una cuenta con ese correo, recibirás un enlace para cambiar la contraseña. Ábrelo en este móvil."
                                } }, enabled = !busy && email.isNotBlank()) { Text("He olvidado mi contraseña") }
                                TextButton(onClick = { importLink = !importLink; recoveryLink = "" }, enabled = !busy) { Text("Ya tengo un enlace de recuperación") }
                                if (importLink) {
                                    OutlinedTextField(recoveryLink, { recoveryLink = it.take(24_000) }, label = { Text("Pega aquí el enlace de recuperación") },
                                        singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy, modifier = Modifier.fillMaxWidth())
                                    OutlinedButton(onClick = { run {
                                        accounts.openAuthCallback(recoveryLink, allowLegacyLocalhost = true)
                                        recoveryLink = ""; importLink = false
                                    } }, enabled = !busy && recoveryLink.isNotBlank()) { Text("Continuar con el enlace") }
                                }
                            }
                        } else {
                            Text("El acceso con cuenta estará disponible cuando terminemos de conectar AnimeFlix a la nube.", style = MaterialTheme.typography.bodyLarge)
                        }
                        if (allowPreview) {
                            OutlinedButton(onClick = { run { accounts.beginPreview() } }, enabled = !busy) { Text("Probar perfiles en este móvil") }
                            Text("Vista previa local: los datos de prueba todavía no se envían a la nube.", style = MaterialTheme.typography.bodySmall)
                        }
                        notice?.let { Text(it) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                account.selected == null -> {
                    ProfilePicker(account, busy, error,
                        onSelect = { run { accounts.select(it) } }, onCreate = { run { accounts.createProfile(it) } },
                        onRefresh = { run { accounts.refreshProfiles() } }, onSignOut = { run { accounts.signOut() } })
                }
                else -> key(account.accountId, account.selected?.id) {
                    val repository = remember { ProfileAnimeRepository.create(context) }
                    val sync by repository.syncState.collectAsStateWithLifecycle()
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(repository, lifecycle) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            while (true) { repository.sync(); delay(30_000) }
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        ActiveProfileBar(checkNotNull(account.selected), account.preview, busy, sync.running,
                            onSwitch = { run { accounts.select(null) } }, onSync = { scope.launch { repository.sync() } },
                            avatarIndex = account.profiles.indexOfFirst { it.id == account.selected?.id }.coerceAtLeast(0))
                        sync.message?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Box(Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalProfileViewModelKey provides "${account.accountId}:${account.selected?.id}") { content() }
                        }
                    }
                }
            }
        }
    }
}
