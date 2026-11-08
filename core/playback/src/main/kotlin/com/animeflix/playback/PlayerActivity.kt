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
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlayerScreen(url, intent.getStringExtra("title") ?: "Episodio", onBack = { finish() },
                    tv = intent.getBooleanExtra("tvMode", resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(episodeUrl: String, title: String, onBack: () -> Unit, tv: Boolean = false, historyModel: WatchHistoryViewModel = viewModel()) {
    var activeEpisodeUrl by rememberSaveable { mutableStateOf(episodeUrl) }
    val history by historyModel.entries.collectAsStateWithLifecycle()
    val historyError by historyModel.error.collectAsStateWithLifecycle()
    val watched = history.find { it.episodeUrl == activeEpisodeUrl }?.watched == true
    val position = history.find { it.episodeUrl == activeEpisodeUrl }?.positionMs ?: 0
    LaunchedEffect(activeEpisodeUrl) { historyModel.opened(activeEpisodeUrl) }
    var servers by remember(episodeUrl) { mutableStateOf<List<PlaybackServer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var playerReload by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var originalPage by rememberSaveable { mutableStateOf(true) }
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
    Scaffold(topBar = { TopAppBar(title = { Text(if (activeEpisodeUrl == episodeUrl) title else "Episodio ${activeEpisodeUrl.substringAfterLast('/')}") }, navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = originalPage, onClick = { originalPage = true }, label = { Text("Reproductor de la web") })
                FilterChip(selected = !originalPage, onClick = { originalPage = false }, label = { Text("Elegir servidor") })
            }
            if (originalPage) {
                val startUrl = remember(originalPage, playerReload) { activeEpisodeUrl }
                key(startUrl, playerReload) {
                    EmbeddedPlayer(startUrl, startUrl, Modifier.fillMaxWidth().weight(1f), originalPage = true,
                        onEpisodeChanged = { activeEpisodeUrl = it },
                        lookupProgress = { url -> history.find { it.episodeUrl == url } },
                        onProgress = historyModel::progress, tv = tv)
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
                        lookupProgress = { url -> history.find { it.episodeUrl == url } }, onProgress = historyModel::progress, tv = tv)
                }
                TextButton(onClick = { playerReload++ }) { Text("Recargar reproductor") }
                Spacer(Modifier.weight(1f))
            }
            }
            historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (position > 0) Text("Minuto guardado: ${playbackTime(position)}", style = MaterialTheme.typography.bodySmall)
            FilterChip(selected = watched, onClick = { historyModel.mark(activeEpisodeUrl, !watched) },
                label = { Text(if (watched) "Visto · desmarcar" else "Marcar episodio como visto") })
            OutlinedButton(onClick = {
                try { uriHandler.openUri(activeEpisodeUrl) }
                catch (_: android.content.ActivityNotFoundException) { error = "No hay un navegador disponible." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir episodio en el navegador") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedPlayer(url: String, episodeUrl: String, modifier: Modifier, originalPage: Boolean = false,
    onEpisodeChanged: (String) -> Unit = {}, lookupProgress: (String) -> WatchEntry? = { null },
    onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }, tv: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var fullView by remember { mutableStateOf<View?>(null) }
    var fullCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val episodeChanged by rememberUpdatedState(onEpisodeChanged)
    val lookup by rememberUpdatedState(lookupProgress)
    val saveProgress by rememberUpdatedState(onProgress)
    val controlsFocus = remember { FocusRequester() }
    LaunchedEffect(tv) { if(tv) controlsFocus.requestFocus() }
    val webView = remember(url) {
        RemotePlayerWebView(context, tv).apply {
            onExitControls = { controlsFocus.requestFocus() }
            val progress = WebPlaybackProgress(this, episodeUrl, { lookup(it) }, { page, position, duration -> saveProgress(page, position, duration) })
            progress.install()
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
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
        }
    }
    Column(modifier) {
        if(tv) {
            Button(onClick = { webView.controlVideo() }, modifier = Modifier.focusRequester(controlsFocus)) { Text("Controlar vídeo con mando") }
            Text("Cruceta: mover puntero · OK: pulsar · Atrás: salir del puntero", style = MaterialTheme.typography.bodySmall)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text("El servidor no está disponible o no permite la reproducción aquí. Prueba otro o abre el episodio en el navegador.")
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
    if (fullView != null) {
        val hideFullScreen = {
            fullView = null
            fullCallback?.onCustomViewHidden()
            fullCallback = null
        }
        Dialog(onDismissRequest = hideFullScreen,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            AndroidView(factory = { fullView!! }, modifier = Modifier.fillMaxSize())
        }
        BackHandler(onBack = hideFullScreen)
    }
}
