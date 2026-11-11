package com.animeflix.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.animeflix.data.AccountManager
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun openDownloads(context: Context) { context.startActivity(Intent(context, DownloadLibraryActivity::class.java)) }

class DownloadLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = AnimeFlixColors) { DownloadLibrary(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadLibrary(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EpisodeDownloads(context) }
    val account by remember { AccountManager.create(context) }.state.collectAsState()
    val owner = "${account.accountId}:${account.selected?.id}"
    var items by remember(owner) { mutableStateOf(emptyList<OfflineEpisode>()) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    var target by remember(owner) { mutableStateOf<OfflineEpisode?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { store.resumeQueue() }
    LaunchedEffect(owner, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while(true) {
            try { items = withContext(Dispatchers.IO) { store.list() } }
            catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                error = "No se pudieron consultar las descargas del dispositivo."
            }
            delay(1_000)
        }
    } }
    Scaffold(topBar = { TopAppBar(title = { Text("Descargas") }, navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Tu biblioteca sin conexión", style = MaterialTheme.typography.headlineSmall)
                Text("${account.selected?.name.orEmpty()} · Solo en este dispositivo", color = AnimeFlixColors.onSurfaceVariant)
                Text("${offlineSize(items.sumOf { it.storedBytes })} usados · ${items.count { it.complete }} disponibles", style = MaterialTheme.typography.bodySmall)
                Text("Una descarga cada vez, alternando fuentes compatibles sin cambiar de idioma. Por defecto se espera una red sin coste medido.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if(items.isEmpty()) item { Text("Todavía no tienes episodios descargados en este perfil.") }
            items(items, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(entry.episodeUrl.substringBeforeLast('/').substringAfterLast('/').replace('-', ' '), color = AnimeFlixColors.onSurfaceVariant)
                        Text(entry.serverName, style = MaterialTheme.typography.labelMedium, color = AnimeFlixColors.primary)
                        Text(entry.statusLabel(), style = MaterialTheme.typography.bodySmall)
                        if(entry.active && (entry.total > 0 || entry.percent >= 0)) LinearProgressIndicator(progress = { (if(entry.percent >= 0) entry.percent / 100f else entry.bytes.toFloat() / entry.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if(entry.complete) Button(onClick = {
                                context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", entry.episodeUrl)
                                    .putExtra("title", entry.title).putExtra("offlineId", entry.id))
                            }, modifier = Modifier.tvRemoteFocus()) { Text("Ver offline") }
                            if(entry.failed) OutlinedButton(enabled = !busy, onClick = {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        store.enqueue(entry.episodeUrl, entry.title, PlaybackServer(entry.serverName, entry.serverUrl))
                                        withContext(Dispatchers.IO) { store.remove(entry.id) }
                                    } catch(failure: Exception) {
                                        if(failure is CancellationException) throw failure
                                        error = "No se pudo reiniciar. Comprueba la conexión, el espacio y la disponibilidad del servidor."
                                    } finally { busy = false }
                                }
                            }, modifier = Modifier.tvRemoteFocus()) { Text("Reintentar") }
                            TextButton(onClick = { target = entry }, modifier = Modifier.tvRemoteFocus()) { Text(if(entry.active) "Cancelar" else "Eliminar") }
                        }
                    }
                }
            }
        }
    }
    target?.let { entry ->
        AlertDialog(onDismissRequest = { target = null }, title = { Text(if(entry.active) "Cancelar descarga" else "Eliminar descarga") },
            text = { Text("Se eliminará el archivo de este dispositivo. Tu historial y progreso se conservarán.") },
            confirmButton = { TextButton(onClick = {
                target = null
                scope.launch {
                    try { withContext(Dispatchers.IO) { store.remove(entry.id) }; items = withContext(Dispatchers.IO) { store.list() } }
                    catch(failure: Exception) {
                        if(failure is CancellationException) throw failure
                        error = "No se pudo eliminar la descarga. Comprueba que el almacenamiento esté disponible."
                    }
                }
            }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { target = null }) { Text("Conservar") } })
    }
}

/** Resolution stays in the foreground; the system continues the actual transfer after leaving the app. */
@Composable
fun DownloadEpisodeButton(episodeUrl: String, title: String, server: PlaybackServer? = null, compact: Boolean = false) {
    val context = LocalContext.current
    val store = remember { EpisodeDownloads(context) }
    val scope = rememberCoroutineScope()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* Queue works even if notifications are declined. */ }
    var selected by remember(episodeUrl, server?.embedUrl) { mutableStateOf(server) }
    var confirm by remember(episodeUrl, server?.embedUrl) { mutableStateOf(false) }
    var metered by remember(episodeUrl) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(episodeUrl) { mutableStateOf<String?>(null) }
    fun prepare() {
        if(selected != null) { confirm = true; return }
        busy = true; message = null
        scope.launch {
            try {
                selected = com.animeflix.data.AnimeAv1Episodes().servers(episodeUrl).firstOrNull { com.animeflix.data.DownloadOriginPolicy.provider(it) != null }
                if(selected == null) message = "Este episodio no tiene una fuente de descarga compatible. Se admiten MP4Upload, Voe y UPNShare cuando el proveedor lo permite."
                else confirm = true
            } catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                message = "No se pudieron consultar las fuentes. Comprueba la conexión y reintenta."
            } finally { busy = false }
        }
    }
    if(compact) {
        IconButton(enabled = !busy, modifier = Modifier.tvRemoteFocus().semantics { contentDescription = "Descargar $title" }, onClick = ::prepare) { Text("↓", style = MaterialTheme.typography.titleLarge) }
        message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Descargas") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Aceptar") } }) }
    } else Column {
        OutlinedButton(enabled = !busy, modifier = Modifier.tvRemoteFocus(), onClick = ::prepare) { Text(if(busy) "Preparando descarga…" else "↓ Descargar episodio") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Descargar para ver offline") },
        text = { Column {
            Text("Fuentes automáticas · ${selected?.name?.substringBefore(" · ") ?: "Idioma original"}. Se guardará en este dispositivo para el perfil actual. El tamaño aparecerá en Descargas al comenzar.")
            Row { Checkbox(checked = metered, onCheckedChange = { metered = it }, modifier = Modifier.semantics { contentDescription = "Permitir descargas en redes con coste medido" }); Text("Permitir datos móviles o redes con coste medido") }
        } }, confirmButton = { TextButton(onClick = {
            confirm = false; busy = true; message = null
            if(android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            scope.launch {
                try { store.enqueue(episodeUrl, title, checkNotNull(selected), metered); message = "Añadido a Descargas." }
                catch(failure: Exception) {
                    if(failure is CancellationException) throw failure
                    message = if(failure is IllegalStateException) failure.message else "No se pudo descargar. El servidor puede no permitirlo o no quedar espacio."
                } finally { busy = false }
            }
        }) { Text("Descargar") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}
