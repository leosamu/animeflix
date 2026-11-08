package com.animeflix.playback

import android.util.Log
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.animeflix.data.Mp4UploadResolver
import com.animeflix.data.ResolvedVideo
import com.animeflix.model.PlaybackServer
import com.animeflix.model.playbackTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeServerScreen(title: String, episodeUrl: String, servers: List<PlaybackServer>, loading: Boolean,
    serverError: String?, position: Long, watched: Boolean, onProgress: (String, Long, Long) -> Unit,
    onMark: () -> Unit, onBack: () -> Unit, onWeb: () -> Unit) {
    var selectedUrl by rememberSaveable(episodeUrl) { mutableStateOf<String?>(null) }
    val selected = servers.find { it.embedUrl == selectedUrl } ?: servers.firstOrNull { Mp4UploadResolver.supports(it.embedUrl) }
        ?: servers.firstOrNull { ProviderPolicy.supports(it.embedUrl) }
    var retry by remember { mutableIntStateOf(0) }
    // An old cancelled request must not clear the state of a newly selected provider.
    var video by remember(episodeUrl, selected?.embedUrl, retry) { mutableStateOf<ResolvedVideo?>(null) }
    var resolving by remember(episodeUrl, selected?.embedUrl, retry) { mutableStateOf(false) }
    var error by remember(episodeUrl, selected?.embedUrl, retry) { mutableStateOf<String?>(null) }
    var fullScreen by rememberSaveable(episodeUrl) { mutableStateOf(false) }
    val activity = LocalContext.current.findPlayerActivity()
    BackHandler(enabled = fullScreen) { fullScreen = false }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        onDispose {
            if(activity != null) {
                activity.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }
    }
    LaunchedEffect(activity, fullScreen) {
        activity?.let {
            it.requestedOrientation = if(fullScreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(it.window, !fullScreen)
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if(fullScreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(episodeUrl, selected?.embedUrl, retry) {
        video = null; error = null
        resolving = false
        if(selected == null) return@LaunchedEffect
        if(!Mp4UploadResolver.supports(selected.embedUrl)) {
            if(ProviderPolicy.supports(selected.embedUrl)) resolving = true
            else error = "Este servidor todavía no es compatible con reproducción nativa. Elige otro servidor."
            return@LaunchedEffect
        }
        resolving = true
        try { video = Mp4UploadResolver().resolve(selected) }
        catch(failure: Exception) {
            if(failure is CancellationException) throw failure
            Log.w("AnimeFlixNative", "No se pudo resolver el vídeo: ${failure.javaClass.simpleName}")
            error = "No se pudo obtener el vídeo de este servidor. Reintenta o elige otro."
        } finally { resolving = false }
    }
    Scaffold(topBar = { if(!fullScreen) TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) },
        contentWindowInsets = if(fullScreen) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(if(fullScreen) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if(fullScreen) 0.dp else 12.dp)) {
            if(!fullScreen) {
            Text("Reproducción nativa", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { server -> FilterChip(selected = selected?.embedUrl == server.embedUrl,
                    onClick = { selectedUrl = server.embedUrl }, label = { Text(server.name) }) }
            }
            if(android.net.Uri.parse(selected?.embedUrl.orEmpty()).host in setOf("player.zilla-networks.com", "byselapuix.com"))
                Text("Servidor en pruebas. Si falla, elige Voe, UPNShare o MP4Upload.", style = MaterialTheme.typography.bodySmall)
            if(loading || resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: serverError)?.let { Text(it); TextButton(onClick = { retry++ }) { Text("Reintentar") } }
            if(!loading && selected == null) Text("Este episodio no ofrece un servidor nativo compatible. Puedes probar el modo web.")
            }
            if(resolving && selected != null && ProviderPolicy.supports(selected.embedUrl)) {
                key(episodeUrl, selected.embedUrl, retry) {
                    ProviderVideoResolver(selected, episodeUrl, onResolved = { video = it; resolving = false },
                        onFailure = { error = it; resolving = false })
                }
            }
            video?.let { source -> key(episodeUrl, source.url, retry) {
                NativeVideo(source, episodeUrl, position, Modifier.fillMaxWidth().weight(1f), onProgress, onRetry = { retry++ },
                    fullScreen = fullScreen, onFullScreen = { fullScreen = it })
            } } ?: Spacer(Modifier.weight(1f))
            if(!fullScreen) {
            OutlinedButton(onClick = { fullScreen = true }, enabled = video != null, modifier = Modifier.fillMaxWidth()) { Text("Pantalla completa") }
            if(position > 0) Text("Minuto guardado: ${playbackTime(position)}", style = MaterialTheme.typography.bodySmall)
            FilterChip(selected = watched, onClick = onMark, label = { Text(if(watched) "Visto · desmarcar" else "Marcar como visto") })
            OutlinedButton(onClick = onWeb, modifier = Modifier.fillMaxWidth()) { Text("Usar reproductor web") }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun NativeVideo(source: ResolvedVideo, episodeUrl: String, resumePosition: Long, modifier: Modifier,
    onProgress: (String, Long, Long) -> Unit, onRetry: () -> Unit, fullScreen: Boolean, onFullScreen: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val save by rememberUpdatedState(onProgress)
    val changeFullScreen by rememberUpdatedState(onFullScreen)
    var failure by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    var played by remember { mutableStateOf(false) }
    var firstFrame by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    val player = remember(source.url, episodeUrl) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(source.userAgent)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(buildMap {
                put("Referer", source.referer)
                source.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                source.origin?.let { put("Origin", it) }
            })
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.Builder().setUri(source.url).setMimeType(source.mimeType).build())
            if(resumePosition > 0) seekTo(resumePosition)
            prepare(); playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
    }
    fun persist() {
        val duration = player.duration
        val position = player.currentPosition
        if(played && duration in 60_000..86_400_000 && position in 0..duration) save(episodeUrl, position, duration)
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if(isPlaying) played = true else persist() }
            override fun onPlayerError(error: PlaybackException) {
                failure = true
                val httpError = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                rejected = httpError?.responseCode == 403
                Log.w("AnimeFlixNative", "Fallo: ${error.errorCodeName}; HTTP=${httpError?.responseCode}; host=${httpError?.dataSpec?.uri?.host}; causa=${error.cause?.javaClass?.simpleName}")
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE) { persist(); player.pause() }
        }
        lifecycle.addObserver(observer)
        onDispose { persist(); lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player) { while(true) { delay(5_000); persist() } }
    Column(modifier) {
        if(buffering && !failure) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failure) {
            Text(if(rejected) "Este servidor ha rechazado la reproducción. Prueba otro servidor o el modo web."
                else "No se pudo reproducir el vídeo. El enlace puede haber caducado o el servidor no responder.")
            TextButton(onClick = onRetry) { Text("Obtener enlace nuevo") }
        }
        AndroidView(factory = { PlayerView(it).apply {
            this.player = player; keepScreenOn = true; useController = true
            setFullscreenButtonClickListener { value -> changeFullScreen(value) }
        } }, update = { it.setFullscreenButtonState(fullScreen) },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = if(firstFrame) "Vídeo listo" else "Cargando vídeo" },
            onRelease = { it.player = null })
    }
}

private fun Context.findPlayerActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}
