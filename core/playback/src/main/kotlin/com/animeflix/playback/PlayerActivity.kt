package com.animeflix.playback

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.model.PlaybackServer
import com.animeflix.model.WatchEntry
import com.animeflix.model.playbackTime
import kotlinx.coroutines.CancellationException

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("episodeUrl").orEmpty()
        if (!AnimeAv1Episodes.isEpisodeUrl(url)) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = AnimeFlixColors) {
                PlayerScreen(url, intent.getStringExtra("title") ?: "Episodio", onBack = { finish() },
                    tv = intent.getBooleanExtra("tvMode", resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION),
                    preferNative = intent.getBooleanExtra("nativeMode", true), offlineId = intent.getLongExtra("offlineId", -1))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(episodeUrl: String, title: String, onBack: () -> Unit, tv: Boolean = false, preferNative: Boolean = true, offlineId: Long = -1, historyModel: WatchHistoryViewModel = viewModel()) {
    var activeEpisodeUrl by rememberSaveable { mutableStateOf(episodeUrl) }
    var activeOfflineId by rememberSaveable { mutableLongStateOf(offlineId) }
    var autoGeneration by rememberSaveable { mutableIntStateOf(0) }
    var automaticEpisode by rememberSaveable { mutableStateOf<String?>(null) }
    val history by historyModel.entries.collectAsStateWithLifecycle()
    val historyError by historyModel.error.collectAsStateWithLifecycle()
    val watched = history.find { it.episodeUrl == activeEpisodeUrl }?.watched == true
    val position = if (automaticEpisode == activeEpisodeUrl) 0 else history.find { it.episodeUrl == activeEpisodeUrl }?.let {
        if (com.animeflix.data.playbackReachedEnd(it.positionMs, it.durationMs)) 0 else it.positionMs
    } ?: 0
    val activeTitle = if (activeEpisodeUrl == episodeUrl) title else "Episodio ${activeEpisodeUrl.substringAfterLast('/')}"
    val completing by historyModel.completing.collectAsStateWithLifecycle()
    val onEnded: (String, Long, Long) -> Unit = { url, played, duration ->
        if (url == activeEpisodeUrl) historyModel.completed(url, played, duration, activeOfflineId != -1L) { next ->
            if (activeEpisodeUrl == url) {
                automaticEpisode = next.url
                activeOfflineId = next.offlineId
                activeEpisodeUrl = next.url
                autoGeneration++
            }
        }
    }
    val onProgress: (String, Long, Long) -> Unit = { url, played, duration ->
        historyModel.progress(url, played, duration)
        if (url == activeEpisodeUrl && played > 0) automaticEpisode = null
    }
    LaunchedEffect(activeEpisodeUrl) { historyModel.opened(activeEpisodeUrl) }
    if(activeOfflineId != -1L) {
        OfflinePlayerScreen(activeOfflineId, activeEpisodeUrl, activeTitle, watched, tv,
            lookupPosition = { if (automaticEpisode == activeEpisodeUrl) 0 else historyModel.savedPosition(activeEpisodeUrl) },
            onProgress = onProgress, onMark = { historyModel.mark(activeEpisodeUrl, !watched) }, onBack = onBack, onEnded = onEnded,
            message = historyError, completing = completing)
        return
    }
    var servers by remember(activeEpisodeUrl) { mutableStateOf<List<PlaybackServer>>(emptyList()) }
    var loading by remember(activeEpisodeUrl) { mutableStateOf(true) }
    var error by remember(activeEpisodeUrl) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var playerReload by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var originalPage by rememberSaveable { mutableStateOf(true) }
    var nativeMode by rememberSaveable { mutableStateOf(preferNative) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(activeEpisodeUrl, retry) {
        loading = true; error = null
        try {
            servers = AnimeAv1Episodes().servers(activeEpisodeUrl)
            if (servers.isEmpty()) error = "No hay reproductores disponibles para este episodio."
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            android.util.Log.w("AnimeFlixPlayer", "No se pudieron cargar los reproductores", failure)
            error = "No se pudieron cargar los reproductores. Comprueba la conexión y reintenta."
        } finally { loading = false }
    }
    if(nativeMode) {
        Box(Modifier.fillMaxSize()) {
        NativeServerScreen(activeTitle, activeEpisodeUrl, servers, loading, historyError ?: error, position, watched,
            onProgress = onProgress, onMark = { historyModel.mark(activeEpisodeUrl, !watched) },
            onBack = onBack, onWeb = { nativeMode = false }, tv = tv, onEnded = onEnded)
        if (completing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if(tv) {
        TvPlayerLayout(if(activeEpisodeUrl == episodeUrl) title else "Episodio ${activeEpisodeUrl.substringAfterLast('/')}",
            servers, loading, historyError ?: error, watched, if(position > 0) "Minuto guardado: ${playbackTime(position)}" else null,
            onMark = { historyModel.mark(activeEpisodeUrl, !watched) }, onBack = onBack,
            onNative = { nativeMode = true }) { usePage, index, fit, reload, controlsActive, showMenu ->
            val startUrl = remember(usePage, reload, autoGeneration) { activeEpisodeUrl }
            val serverUrl = servers.getOrNull(index)?.embedUrl
            if(usePage || serverUrl != null) key(usePage, startUrl, if(usePage) null else serverUrl, reload) {
                EmbeddedPlayer(if(usePage) startUrl else serverUrl!!, if(usePage) startUrl else activeEpisodeUrl,
                    Modifier.fillMaxSize(), originalPage = usePage, tv = true, tvFullScreen = true,
                    fitVideo = fit, controlsActive = controlsActive, onControlsRequested = showMenu,
                    onEpisodeChanged = { activeEpisodeUrl = it }, lookupProgress = { url -> history.find { it.episodeUrl == url } },
                    onProgress = onProgress, onEnded = onEnded, autoPlay = automaticEpisode == activeEpisodeUrl)
            }
        }
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (activeEpisodeUrl == episodeUrl) title else "Episodio ${activeEpisodeUrl.substringAfterLast('/')}") }, navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { nativeMode = true }) { Text("Usar reproducción nativa") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = originalPage, onClick = { originalPage = true }, label = { Text("Reproductor de la web") })
                FilterChip(selected = !originalPage, onClick = { originalPage = false }, label = { Text("Elegir servidor") })
            }
            if (originalPage) {
                val startUrl = remember(originalPage, playerReload, autoGeneration) { activeEpisodeUrl }
                key(startUrl, playerReload) {
                    EmbeddedPlayer(startUrl, startUrl, Modifier.fillMaxWidth().weight(1f), originalPage = true,
                        onEpisodeChanged = { activeEpisodeUrl = it },
                        lookupProgress = { url -> history.find { it.episodeUrl == url } },
                        onProgress = onProgress, tv = tv, onEnded = onEnded, autoPlay = automaticEpisode == activeEpisodeUrl)
                }
                TextButton(onClick = { playerReload++ }) { Text("Recargar reproductor") }
            } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it); OutlinedButton(onClick = { retry++ }) { Text("Reintentar") } }
            if (servers.isNotEmpty() && !loading) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    servers.forEachIndexed { index, server ->
                        FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(server.name) })
                    }
                }
                val server = servers[selected.coerceIn(servers.indices)]
                Text("Pulsa reproducir en el vídeo. Si falla, prueba otro servidor.", style = MaterialTheme.typography.bodySmall)
                key(server.embedUrl, retry, playerReload) {
                    EmbeddedPlayer(server.embedUrl, activeEpisodeUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        lookupProgress = { url -> history.find { it.episodeUrl == url } }, onProgress = onProgress, tv = tv,
                        onEnded = onEnded, autoPlay = automaticEpisode == activeEpisodeUrl)
                }
                TextButton(onClick = { playerReload++ }) { Text("Recargar reproductor") }
                Spacer(Modifier.weight(1f))
            }
            }
            historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (position > 0) Text("Minuto guardado: ${playbackTime(position)}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = watched, onClick = { historyModel.mark(activeEpisodeUrl, !watched) },
                label = { Text(if (watched) "Visto · desmarcar" else "Marcar visto") })
            OutlinedButton(onClick = {
                try { uriHandler.openUri(activeEpisodeUrl) }
                catch (_: android.content.ActivityNotFoundException) { error = "No hay un navegador disponible." }
            }, modifier = Modifier.weight(1f)) { Text("Abrir en navegador") }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedPlayer(url: String, episodeUrl: String, modifier: Modifier, originalPage: Boolean = false,
    onEpisodeChanged: (String) -> Unit = {}, lookupProgress: (String) -> WatchEntry? = { null },
    onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }, tv: Boolean = false,
    tvFullScreen: Boolean = false, fitVideo: Boolean = true, controlsActive: Boolean = true,
    onControlsRequested: () -> Unit = {}, onEnded: (String, Long, Long) -> Unit = { _, _, _ -> }, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var fullView by remember { mutableStateOf<View?>(null) }
    var fullCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val episodeChanged by rememberUpdatedState(onEpisodeChanged)
    val lookup by rememberUpdatedState(lookupProgress)
    val saveProgress by rememberUpdatedState(onProgress)
    val complete by rememberUpdatedState(onEnded)
    val controlsFocus = remember { FocusRequester() }
    val showControls by rememberUpdatedState(onControlsRequested)
    val fit by rememberUpdatedState(fitVideo)
    val skipScope = rememberCoroutineScope()
    var skip by remember(url) { mutableStateOf<com.animeflix.data.SkipInterval?>(null) }
    var skipAction by remember(url) { mutableStateOf<() -> Unit>({}) }
    val skipFocus = remember { FocusRequester() }
    val skipView = remember { arrayOfNulls<RemotePlayerWebView>(1) }
    val skipControl: @Composable (Modifier) -> Unit = { modifier ->
        skip?.let { interval -> FilledTonalButton(onClick = { skipAction(); skip = null; if(tv) skipView[0]?.controlVideo() },
            modifier = modifier.focusRequester(skipFocus).tvRemoteFocus()) {
            Text(interval.label)
        } }
    }
    LaunchedEffect(tv) { if(tv && !tvFullScreen) controlsFocus.requestFocus() }
    val webView = remember(url) {
        RemotePlayerWebView(context, tv).apply {
            skipView[0] = this
            onExitControls = { if(skip != null) skipFocus.requestFocus() else if(tvFullScreen) showControls() else controlsFocus.requestFocus() }
            val progress = WebPlaybackProgress(this, episodeUrl, { if(autoPlay) null else lookup(it) }, { page, position, duration -> saveProgress(page, position, duration) },
                { page, position, duration -> complete(page, position, duration) }, autoPlay, skipScope,
                { interval, action -> skip = interval; skipAction = action })
            progress.install()
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = !autoPlay
            settings.setSupportMultipleWindows(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, progress: Int) { loading = progress < 100 }
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        android.util.Log.w("AnimeFlixPlayer", "El reproductor web ha comunicado un error de JavaScript")
                        error = true
                    }
                    return true
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (fullView != null) { callback?.onCustomViewHidden(); return }
                    fullView = view; fullCallback = callback
                }
                override fun onHideCustomView() {
                    fullView = null; fullCallback?.onCustomViewHidden(); fullCallback = null
                }
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, pageUrl: String, favicon: android.graphics.Bitmap?) { progress.navigate(pageUrl) }
                override fun onPageFinished(view: WebView, pageUrl: String) {
                    if (originalPage && AnimeAv1Episodes.isEpisodeUrl(pageUrl)) episodeChanged(pageUrl)
                    if(tvFullScreen && originalPage) view.evaluateJavascript(tvVideoLayoutScript(fit), null)
                }
                @Deprecated("Required for Android 6 WebView")
                override fun shouldOverrideUrlLoading(view: WebView, target: String): Boolean {
                    val uri = android.net.Uri.parse(target)
                    return uri.scheme != "https" || uri.host != android.net.Uri.parse(url).host
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Keep external popups and app intents out of the embedded player.
                    if (!request.isForMainFrame) return !AnimeAv1Episodes.isHttpsUrl(request.url.toString())
                    return request.url.scheme != "https" || request.url.host != android.net.Uri.parse(url).host
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame || request.url.toString() == url) error = true
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if ((request.isForMainFrame || request.url.toString() == url) && response.statusCode >= 400) error = true
                }
            }
            setDownloadListener { _, _, _, _, _ -> error = true }
            // Use the published iframe as an embed, rather than navigating to it as a whole page.
            val safeUrl = android.text.TextUtils.htmlEncode(url)
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;background:#000;overflow:hidden">
                <iframe src="$safeUrl" style="border:0;width:100vw;height:100vh" allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                    sandbox="allow-scripts allow-same-origin allow-forms allow-presentation" allowfullscreen></iframe></body></html>"""
            if (originalPage) loadUrl(episodeUrl)
            else loadDataWithBaseURL(episodeUrl, html, "text/html", "UTF-8", null)
        }
    }
    LaunchedEffect(webView, tvFullScreen, controlsActive) {
        if(tvFullScreen && controlsActive) webView.post { webView.controlVideo() }
        else if(tvFullScreen) webView.releaseControls()
    }
    LaunchedEffect(webView, fitVideo) {
        if(tvFullScreen && originalPage) webView.evaluateJavascript(tvVideoLayoutScript(fitVideo), null)
    }
    DisposableEffect(webView, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { webView.onResume(); webView.resumeTimers() }
                Lifecycle.Event.ON_PAUSE -> {
                    webView.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause()})", null)
                    webView.onPause(); webView.pauseTimers()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            fullView = null; fullCallback?.onCustomViewHidden(); fullCallback = null
            webView.stopLoading(); webView.loadUrl("about:blank"); webView.removeAllViews(); webView.destroy()
            if(skipView[0] === webView) skipView[0] = null
        }
    }
    if(tvFullScreen) {
        Box(modifier) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            skipControl(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp))
        }
    } else Column(modifier) {
        if(tv) {
            Button(onClick = { webView.controlVideo() }, modifier = Modifier.focusRequester(controlsFocus)) { Text("Controlar vídeo con mando") }
            Text("Cruceta: mover puntero · OK: pulsar · Atrás: salir del puntero", style = MaterialTheme.typography.bodySmall)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text("El servidor no está disponible o no permite la reproducción aquí. Prueba otro o abre el episodio en el navegador.")
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            skipControl(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp))
        }
    }
    if (fullView != null) {
        val hideFullScreen = {
            fullView = null
            fullCallback?.onCustomViewHidden()
            fullCallback = null
        }
        Dialog(onDismissRequest = hideFullScreen,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { fullView!! }, modifier = Modifier.fillMaxSize())
                skipControl(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp))
            }
        }
        BackHandler(onBack = hideFullScreen)
    }
}
