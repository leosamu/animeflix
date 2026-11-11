package com.animeflix.playback

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.animeflix.data.ResolvedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflinePlayerScreen(id: Long, episodeUrl: String, title: String, watched: Boolean,
    tv: Boolean, lookupPosition: suspend () -> Long, onProgress: (String, Long, Long) -> Unit, onMark: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val resumeFocus = remember { FocusRequester() }
    val store = remember { EpisodeDownloads(context) }
    var source by remember(id) { mutableStateOf<ResolvedVideo?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var resumePosition by remember(id) { mutableLongStateOf(0) }
    var fullScreen by rememberSaveable(id) { mutableStateOf(tv) }
    LaunchedEffect(fullScreen) { if(tv && !fullScreen) { withFrameNanos { }; resumeFocus.requestFocus() } }
    LaunchedEffect(id) {
        // Reading the local file is faster than the history flow's initial emission.
        // Await the saved position before constructing the player, rather than starting at zero.
        source = try {
            resumePosition = lookupPosition()
            android.util.Log.d("AnimeFlixOffline", "Saved position loaded: $resumePosition")
            withContext(Dispatchers.IO) {
            if(store.list().none { it.id == id && it.episodeUrl == episodeUrl }) null
            else store.localUri(id)?.let { ResolvedVideo(it.toString(), "", mimeType = "video/mp4") }
        } } catch(failure: Exception) {
            if(failure is CancellationException) throw failure
            android.util.Log.w("AnimeFlixOffline", "Local media unavailable: ${failure.javaClass.simpleName}")
            null
        }
        android.util.Log.d("AnimeFlixOffline", "Local file available: ${source != null}")
        loading = false
    }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose { activity?.let {
            it.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } }
    }
    LaunchedEffect(fullScreen) {
        activity?.let {
            it.requestedOrientation = if(tv || fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if(fullScreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    Scaffold(topBar = { if(!fullScreen) TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(if(fullScreen) PaddingValues(0.dp) else padding), verticalArrangement = Arrangement.spacedBy(if(fullScreen) 0.dp else 8.dp)) {
            if(!fullScreen) Text("DESCARGADO · SIN CONEXIÓN", color = AnimeFlixColors.primary, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            source?.let { video ->
                NativeVideo(video, episodeUrl, resumePosition, Modifier.fillMaxWidth().weight(1f), onProgress,
                    onRetry = onBack, fullScreen = fullScreen, onFullScreen = { fullScreen = it }, tv = tv, offline = true)
            }
            if(source == null && !loading) Text("El archivo no está disponible en este perfil. Vuelve a Descargas para descargarlo de nuevo.", modifier = Modifier.padding(16.dp))
            if(!fullScreen) Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { fullScreen = true }, enabled = source != null, modifier = Modifier.tvRemoteFocus().focusRequester(resumeFocus)) { Text("Ver vídeo") }
                OutlinedButton(onClick = onMark, modifier = Modifier.tvRemoteFocus()) { Text(if(watched) "Visto · desmarcar" else "Marcar visto") }
            }
        }
    }
}
