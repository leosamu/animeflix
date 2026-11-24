package com.animeflix.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.ImageButton
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ScriptHandler
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.animeflix.data.DoramaEpisode
import com.animeflix.playback.RemotePlayerWebView
import com.animeflix.playback.TvPlaybackPanel
import com.animeflix.playback.TvPlaybackButton
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val playerOrigins = setOf(
    "https://ghbrisk.com", "https://playmogo.com", "https://primeload.co", "https://streamtape.com", "https://advtpe.com",
    "https://streamtape.to", "https://streamtape.net", "https://streamtapeadblockuser.art", "https://streamwish.to",
    "https://streamwish.com", "https://doodstream.com", "https://dood.to"
)
private const val episodeOrigin = "https://doramasflix.co"
private val fullscreenOrigins = playerOrigins + setOf(episodeOrigin, "https://embedshortener.co")

private val adHosts = setOf(
    "ad-maven.com", "adnxs.com", "adsco.re", "adsterra.com", "clickadu.com",
    "doubleclick.net", "googlesyndication.com", "popads.net", "popcash.net",
    "propellerads.com", "taboola.com", "trafficjunky.net"
)

private fun Uri.isAdRequest(): Boolean {
    val domain = host?.lowercase()?.trimEnd('.') ?: return false
    return adHosts.any { domain == it || domain.endsWith(".$it") }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DoramasPlayer(
    episode: DoramaEpisode,
    title: String,
    resumeMs: Long,
    watched: Boolean,
    onMark: () -> Unit,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onDownload: (DoramaDownloadOffer) -> Unit
) {
    val progress by rememberUpdatedState(onProgress)
    val download by rememberUpdatedState(onDownload)
    val context = LocalContext.current
    val tv = remember(context) {
        context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    val remoteControl = remember { FocusRequester() }
    var tvMenu by remember { mutableStateOf(tv) }
    val preferences = remember(context) { context.getSharedPreferences("doramas_player", 0) }
    var mode by remember {
        mutableStateOf(AdMode.entries.find { it.name == preferences.getString("ad_mode", null) }
            ?: if (preferences.getBoolean("block_ads", true)) AdMode.Original else AdMode.Off)
    }
    var cosmetics by remember { mutableStateOf(preferences.getBoolean("brave_cosmetics", true)) }
    var showBlocker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf(emptyList<PlaybackSource>()) }
    var selectedSource by remember { mutableStateOf<PlaybackSource?>(null) }
    var showSources by remember { mutableStateOf(false) }
    val activeMode = remember { AtomicReference(if (mode == AdMode.Brave) AdMode.Original else mode) }
    val analyzed = remember { AtomicLong() }
    val blocked = remember { AtomicLong() }
    val cosmeticScripts = remember { mutableListOf<ScriptHandler>() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var nativeFullscreen by remember { mutableStateOf(false) }
    var pendingFullscreen by remember { mutableStateOf<String?>(null) }
    val tappedFullscreen = remember { arrayOfNulls<String>(1) }
    var chrome by remember { mutableStateOf<WebChromeClient?>(null) }
    BackHandler {
        when {
            fullscreen != null -> chrome?.onHideCustomView()
            nativeFullscreen -> { nativeFullscreen = false; if (tv) tvMenu = true }
            tv && tvMenu && selectedSource == null -> onBack()
            tv -> tvMenu = !tvMenu
            else -> onBack()
        }
    }
    LaunchedEffect(pendingFullscreen) {
        if (pendingFullscreen == null) return@LaunchedEffect
        delay(1_200)
        pendingFullscreen = null
        if (fullscreen == null) nativeFullscreen = true
    }
    LaunchedEffect(tvMenu, selectedSource, sources.isEmpty(), webView, nativeFullscreen, showBlocker) {
        if (!tv || nativeFullscreen || showBlocker) return@LaunchedEffect
        val player = webView as? RemotePlayerWebView ?: return@LaunchedEffect
        if (tvMenu) {
            player.releaseControls()
            remoteControl.requestFocus()
        } else if (selectedSource != null) player.controlVideo()
    }

    LaunchedEffect(mode, cosmetics, webView) {
        val view = webView ?: return@LaunchedEffect
        cosmeticScripts.forEach { it.remove() }
        cosmeticScripts.clear()
        val previous = activeMode.get()
        if (mode == AdMode.Brave) {
            status = "Cargando EasyList y EasyPrivacy…"
            val ready = BraveBlocker.load()
            status = if (ready) "EasyList y EasyPrivacy activas" else "Listas no disponibles. Se usa el motor original."
            activeMode.set(if (ready) AdMode.Brave else AdMode.Original)
            if (ready && cosmetics && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                for (origin in playerOrigins) {
                    BraveBlocker.cosmeticScript(origin)?.let { script ->
                        cosmeticScripts += WebViewCompat.addDocumentStartJavaScript(view, script, setOf(origin))
                    }
                }
            }
        } else {
            status = ""
            activeMode.set(mode)
        }
        if (previous != activeMode.get() || mode == AdMode.Brave) {
            analyzed.set(0)
            blocked.set(0)
            view.reload()
        }
    }

    if (showBlocker) {
        var counts by remember { mutableStateOf(analyzed.get() to blocked.get()) }
        LaunchedEffect(Unit) {
            while (true) {
                counts = analyzed.get() to blocked.get()
                delay(500)
            }
        }
        AlertDialog(
            onDismissRequest = { showBlocker = false },
            title = { Text("Bloqueador de publicidad") },
            text = {
                Column {
                    AdMode.entries.forEach { option ->
                        fun select() {
                            mode = option
                            preferences.edit().putString("ad_mode", option.name).apply()
                        }
                        Row(Modifier.fillMaxWidth().clickable { select() }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == option, onClick = null)
                            Text(option.label, Modifier.padding(start = 8.dp))
                        }
                    }
                    if (mode == AdMode.Brave) {
                        Text(status)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(cosmetics, onCheckedChange = {
                                cosmetics = it
                                preferences.edit().putBoolean("brave_cosmetics", it).apply()
                            })
                            Text("Filtrado cosmético")
                        }
                    }
                    Text("Peticiones analizadas · ${counts.first}")
                    Text("Peticiones bloqueadas · ${counts.second}")
                }
            },
            confirmButton = { TextButton(onClick = { showBlocker = false }) { Text("Cerrar") } }
        )
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!tv && fullscreen == null) Column {
                Row {
                TextButton(onClick = onBack) { Text("Volver") }
                TextButton(onClick = { showBlocker = true }) { Text("Bloqueo") }
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(episode.url))
                            .addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No hay un navegador disponible.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Abrir en navegador") }
                }
                Text("Temporada ${episode.season} · Episodio ${episode.number}", Modifier.padding(horizontal = 12.dp))
                Box {
                    TextButton(onClick = { showSources = true }, enabled = sources.isNotEmpty()) {
                        Text(selectedSource?.let { "${it.language} · ${it.name}" } ?: "Elegir idioma y servidor")
                    }
                    DropdownMenu(expanded = showSources, onDismissRequest = { showSources = false }) {
                        sources.forEach { source ->
                            DropdownMenuItem(text = { Text("${source.language} · ${source.name}") }, onClick = {
                                selectedSource = source
                                showSources = false
                                webView?.evaluateJavascript("window.kdoramasSelect?.(${source.index})", null)
                            })
                        }
                    }
                }
                if (selectedSource != null) {
                    TextButton(onClick = {
                        if (tv) nativeFullscreen = true
                        else {
                            val requestId = java.util.UUID.randomUUID().toString()
                            pendingFullscreen = requestId
                            webView?.evaluateJavascript("window.kdoramasFullscreen?.(${JSONObject.quote(requestId)})", null)
                        }
                    }, enabled = pendingFullscreen == null) { Text("Pantalla completa") }
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxSize(),
                factory = { context ->
                    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                    val player = RemotePlayerWebView(context, tv).apply {
                        if (tv) {
                            onExitControls = {
                                releaseControls()
                                if (nativeFullscreen) nativeFullscreen = false
                                tvMenu = true
                            }
                            releaseControls()
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(true)
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val width = right - left
                            val height = bottom - top
                            if (width > 0 && height > 0 && (width != oldRight - oldLeft || height != oldBottom - oldTop)) {
                                evaluateJavascript("window.kdoramasResize?.($width, $height)", null)
                            }
                        }
                        setDownloadListener { url, userAgent, disposition, mime, _ ->
                            val offer = DoramaDownloadOffer.from(url, mime, disposition, userAgent.orEmpty())
                            if (offer != null) download(offer)
                            else Toast.makeText(context, "El servidor no ha proporcionado un MP4 descargable.", Toast.LENGTH_SHORT).show()
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (request.isForMainFrame || request.url.scheme !in setOf("http", "https")) return null
                                val engine = activeMode.get()
                                if (engine == AdMode.Off) return null
                                analyzed.incrementAndGet()
                                val matched = if (engine == AdMode.Brave) BraveBlocker.blocks(request, episode.url)
                                else request.url.isAdRequest()
                                return if (matched) {
                                    blocked.incrementAndGet()
                                    WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                                        emptyMap(), ByteArrayInputStream(byteArrayOf()))
                                } else null
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme != "https") return true
                                if (activeMode.get() == AdMode.Original && uri.isAdRequest()) return true
                                if (request.hasGesture() && request.method == "GET") {
                                    DoramaDownloadOffer.from(uri.toString(), null, null, view.settings.userAgentString)?.let {
                                        download(it)
                                        return true
                                    }
                                }
                                return request.isForMainFrame && uri.toString() != episode.url
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (url == episode.url) {
                                    view.evaluateJavascript(sourceFilter, null)
                                    if (activeMode.get() == AdMode.Brave && preferences.getBoolean("brave_cosmetics", true)) {
                                        BraveBlocker.cosmeticScript(url)?.let { view.evaluateJavascript(it, null) }
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                sources = emptyList()
                                selectedSource = null
                            }
                        }
                        val fullscreenClient = object : WebChromeClient() {
                            private var callback: CustomViewCallback? = null
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                if (tv) {
                                    callback.onCustomViewHidden()
                                    tvMenu = false
                                    nativeFullscreen = true
                                    return
                                }
                                if (this.callback != null) {
                                    callback.onCustomViewHidden()
                                    return
                                }
                                this.callback = callback
                                pendingFullscreen = null
                                nativeFullscreen = false
                                fullscreen = view
                            }
                            override fun onHideCustomView() {
                                val previous = callback ?: return
                                callback = null
                                fullscreen = null
                                nativeFullscreen = false
                                previous.onCustomViewHidden()
                            }
                        }
                        webChromeClient = fullscreenClient
                        chrome = fullscreenClient
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            WebViewCompat.addWebMessageListener(this, "DoramaSources", setOf("https://doramasflix.co")) { _, message, _, main, _ ->
                                if (main) {
                                    sources = runCatching {
                                        val rows = JSONArray(message.data.orEmpty())
                                        (0 until minOf(rows.length(), 100)).map { index ->
                                            val row = rows.getJSONArray(index)
                                            PlaybackSource(index, row.getString(0).take(100), row.getString(1).take(100))
                                        }
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                        ) {
                            WebViewCompat.addWebMessageListener(this, "DoramaFullscreen", setOf(episodeOrigin)) { view, message, _, main, _ ->
                                if (main) {
                                    val point = runCatching { JSONObject(message.data.orEmpty()) }.getOrNull()
                                    val id = point?.optString("id")
                                    if (id != null && id == pendingFullscreen && id != tappedFullscreen[0]) {
                                        val x = point.optDouble("x")
                                        val y = point.optDouble("y")
                                        if (x in 0.0..1.0 && y in 0.0..1.0) {
                                            tappedFullscreen[0] = id
                                            view.tapAt((x * view.width).toFloat(), (y * view.height).toFloat())
                                        }
                                    }
                                }
                            }
                            WebViewCompat.addDocumentStartJavaScript(this, fullscreenBridge, fullscreenOrigins)
                            WebViewCompat.addWebMessageListener(this, "DoramaProgress", playerOrigins) { _, message, origin, main, _ ->
                                if (!main && origin.isPlayerOrigin()) {
                                    val sample = runCatching { JSONObject(message.data.orEmpty()) }.getOrNull()
                                    val duration = sample?.optLong("duration") ?: 0
                                    val position = sample?.optLong("position") ?: 0
                                    if (duration in 60_000..86_400_000 && position in 0..duration) progress(position, duration)
                                }
                            }
                            WebViewCompat.addDocumentStartJavaScript(this, progressScript(resumeMs), playerOrigins)
                        }
                        webView = this
                        loadUrl(episode.url)
                    }
                    FrameLayout(context).apply {
                        addView(player, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = {
                    val player = webView
                    webView = null
                    chrome?.onHideCustomView()
                    player?.stopLoading()
                    player?.destroy()
                }
            )
        }
        if (tv && tvMenu && !nativeFullscreen) {
            TvPlaybackPanel("$title · T${episode.season} E${episode.number}", Modifier.align(Alignment.CenterStart)) {
                TvPlaybackButton("Ver vídeo · ocultar controles", { tvMenu = false },
                    if (selectedSource != null) Modifier.focusRequester(remoteControl) else Modifier, enabled = selectedSource != null)
                Text("Cruceta: puntero · OK: pulsar · Atrás o Menú: controles", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                TvPlaybackButton("Pantalla completa", { nativeFullscreen = true; tvMenu = false }, enabled = selectedSource != null)
                TvPlaybackButton("Recargar reproductor", { webView?.reload() },
                    if (sources.isEmpty() && selectedSource == null) Modifier.focusRequester(remoteControl) else Modifier)
                TvPlaybackButton("Bloqueo de publicidad", { showBlocker = true })
                TvPlaybackButton(if (watched) "Visto · desmarcar" else "Marcar como visto", onMark)
                Text("Idioma y servidor", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                if (sources.isEmpty()) Text("Cargando servidores…")
                sources.forEachIndexed { index, source ->
                    TvPlaybackButton("${source.language} · ${source.name}" + if (source == selectedSource) " ✓" else "", {
                        selectedSource = source
                        webView?.evaluateJavascript("window.kdoramasSelect?.(${source.index})", null)
                        tvMenu = false
                    }, if (index == 0 && selectedSource == null) Modifier.focusRequester(remoteControl) else Modifier)
                }
                TvPlaybackButton("Volver a los episodios", onBack)
            }
        }
    }
    val fullscreenView = fullscreen
    if (fullscreenView != null) FullscreenVideo(fullscreenView)
    else if (nativeFullscreen) webView?.let {
        FullscreenVideo(it, tv) { nativeFullscreen = false; if (tv) tvMenu = true }
    }
}

@Composable
private fun FullscreenVideo(video: View, tv: Boolean = false, exit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val onExit by rememberUpdatedState(exit)
    DisposableEffect(activity, video, exit != null) {
        val window = activity?.window
        val decor = window?.decorView as? ViewGroup
        if (window == null || decor == null) return@DisposableEffect onDispose {}
        val container = FrameLayout(decor.context).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusableInTouchMode = true
        }
        val insets = WindowCompat.getInsetsController(window, decor)
        val previousBehavior = insets.systemBarsBehavior
        val previousOrientation = activity.requestedOrientation
        val initialInsets = ViewCompat.getRootWindowInsets(decor)
        val restoreStatus = initialInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) != false
        val restoreNavigation = initialInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) != false
        val content = decor.findViewById<View>(android.R.id.content)
        val previousAccessibility = content?.importantForAccessibility
        val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
        val originalParent = video.parent as? ViewGroup
        val originalIndex = originalParent?.indexOfChild(video) ?: 0
        val originalParams = video.layoutParams
        originalParent?.removeView(video)
        container.addView(video, FrameLayout.LayoutParams(matchParent, matchParent))
        if (exit != null) {
            val close = ImageButton(decor.context).apply {
                contentDescription = "Salir de pantalla completa"
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(Color.WHITE)
                setBackgroundColor(0x70000000)
                setOnClickListener { onExit?.invoke() }
            }
            val size = (48 * decor.resources.displayMetrics.density).toInt()
            container.addView(close, FrameLayout.LayoutParams(
                size, size, Gravity.TOP or Gravity.END))
        }
        decor.addView(container, ViewGroup.LayoutParams(matchParent, matchParent))
        content?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        container.requestFocus()
        if (tv) (video as? RemotePlayerWebView)?.controlVideo()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (tv) (video as? RemotePlayerWebView)?.releaseControls()
            container.removeView(video)
            decor.removeView(container)
            if (exit != null && originalParent?.isAttachedToWindow == true) {
                originalParent.addView(video, originalIndex.coerceIn(0, originalParent.childCount), originalParams)
            }
            if (previousAccessibility != null) content.importantForAccessibility = previousAccessibility
            insets.systemBarsBehavior = previousBehavior
            if (restoreStatus) insets.show(WindowInsetsCompat.Type.statusBars())
            if (restoreNavigation) insets.show(WindowInsetsCompat.Type.navigationBars())
            activity.requestedOrientation = previousOrientation
        }
    }
}

private fun WebView.tapAt(x: Float, y: Float) {
    val now = SystemClock.uptimeMillis()
    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
        val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}

private val fullscreenBridge = """
    (() => {
      const request = 'kdoramas-fullscreen-request';
      const target = 'kdoramas-fullscreen-target';
      const origins = new Set(${JSONArray(fullscreenOrigins.toList())});
      let activeId = null;
      const frames = () => [...document.querySelectorAll('iframe')];
      function route(id) {
        activeId = id;
        const video = [...document.querySelectorAll('video')].find(v => v.offsetWidth > 0 && v.offsetHeight > 0);
        if (!video || window === window.top) {
          for (const frame of frames()) frame.contentWindow?.postMessage({ type: request, id }, '*');
          return;
        }
        const button = [...document.querySelectorAll('button,[role=button]')].find(element =>
          /^(enter fullscreen|enter full screen|pantalla completa|full screen|fullscreen)$/i.test(
            (element.getAttribute('aria-label') || element.title || element.textContent).trim()
          ) && element.getBoundingClientRect().width > 0);
        const rect = video.getBoundingClientRect();
        const x = Math.min(innerWidth - 8, Math.max(8, rect.x + rect.width / 2));
        const y = Math.min(innerHeight - 8, Math.max(8, rect.y + rect.height / 2));
        if (!(x > 0 && y > 0)) return;
        const trigger = document.createElement('button');
        trigger.style.cssText = 'position:fixed;z-index:2147483647;width:32px;height:32px;opacity:0;';
        trigger.style.left = (x - 16) + 'px';
        trigger.style.top = (y - 16) + 'px';
        trigger.addEventListener('click', event => {
          trigger.remove();
          if (!event.isTrusted) return;
          if (button) button.click();
          setTimeout(() => {
            if (document.fullscreenEnabled && !document.fullscreenElement && navigator.userActivation?.isActive) {
              video.requestFullscreen?.().catch(() => {});
            }
          }, button ? 120 : 0);
        }, { once: true });
        document.body.append(trigger);
        setTimeout(() => trigger.remove(), 850);
        if (document.elementFromPoint(x, y) !== trigger) { trigger.remove(); return; }
        parent.postMessage({ type: target, id, x, y }, '*');
      }
      if (window === window.top) window.kdoramasFullscreen = route;
      addEventListener('message', event => {
        const data = event.data;
        if (!origins.has(event.origin) || !data || typeof data.id !== 'string') return;
        if (data.type === request && window !== window.top && event.source === parent) route(data.id);
        if (data.type !== target || data.id !== activeId) return;
        const frame = frames().find(candidate => candidate.contentWindow === event.source);
        if (!frame) return;
        const r = frame.getBoundingClientRect();
        const x = r.x + frame.clientLeft + data.x;
        const y = r.y + frame.clientTop + data.y;
        if (!(x >= r.x && x < r.right && y >= r.y && y < r.bottom) ||
            document.elementFromPoint(x, y) !== frame) return;
        if (window === window.top && window.DoramaFullscreen) {
          DoramaFullscreen.postMessage(JSON.stringify({ id: data.id, x: x / innerWidth, y: y / innerHeight }));
        } else parent.postMessage({ type: target, id: data.id, x, y }, '*');
      });
    })();
""".trimIndent()

private fun Uri.isPlayerOrigin(): Boolean = "https://${host}" in playerOrigins &&
    scheme == "https" && (port == -1 || port == 443)

private data class PlaybackSource(val index: Int, val language: String, val name: String)

private val sourceFilter = """
    (() => {
      if (!window.DoramaSources) return;
      if (document.getElementById('kdoramas-player-style')) return;
      const style = document.createElement('style');
      style.id = 'kdoramas-player-style';
      style.textContent = `
        html, body { overflow: hidden !important; background: #10101a !important; }
        body * { visibility: hidden !important; }
        [data-kdorama-frame] { visibility: hidden !important; }
        html[data-kdorama-selected] [data-kdorama-frame] {
          position: fixed !important; inset: 0 !important;
          width: 100vw !important; height: var(--kdoramas-height, 220px) !important;
          z-index: 2147483647 !important; visibility: visible !important;
        }
        html[data-kdorama-selected] [data-kdorama-frame] iframe {
          visibility: visible !important; width: 100% !important;
          height: var(--kdoramas-height, 220px) !important;
        }
      `;
      document.head.append(style);
      function resizePlayer() {
        window.kdoramasResize();
      }
      window.kdoramasResize = (width, height) => {
        const viewportHeight = width && height ? height * innerWidth / width : innerHeight;
        document.documentElement.style.setProperty('--kdoramas-height', Math.max(1, viewportHeight) + 'px');
      };
      resizePlayer();
      window.addEventListener('resize', resizePlayer);

      function playerFrame() {
        return document.querySelector('iframe[src^="https://embedshortener.co/"]');
      }

      let buttons = [];
      let last = '';
      function filter() {
        const frame = playerFrame();
        if (frame) {
          frame.parentElement.setAttribute('data-kdorama-frame', '');
          frame.allowFullscreen = true;
          if (!frame.allow.split(';').some(value => value.trim().startsWith('fullscreen'))) {
            frame.allow += '; fullscreen';
          }
        }
        buttons = [];
        const options = [];
        for (const group of document.querySelectorAll('div.bg-card')) {
          const label = group.firstElementChild?.textContent.trim();
          const grid = group.firstElementChild?.nextElementSibling;
          if (!label || !grid?.classList.contains('flex-wrap')) continue;
          const choices = [...grid.querySelectorAll(':scope > button')];
          if (!choices.length) continue;
          for (const choice of choices) {
            const text = choice.textContent.trim().replace(/^★\s*/, '').replace(/\s*Mejors*$/i, '').trim();
            if (!text) continue;
            buttons.push(choice);
            options.push([label.replace(/^CoreanoES$/i, 'Coreano · sub. español'), text]);
          }
        }
        const snapshot = JSON.stringify(options);
        if (snapshot !== last) {
          last = snapshot;
          DoramaSources.postMessage(snapshot);
        }
      }
      window.kdoramasSelect = index => {
        const button = buttons[index];
        if (!button?.isConnected) return;
        document.documentElement.setAttribute('data-kdorama-selected', '');
        button.click();
      };
      filter();
      new MutationObserver(filter).observe(document.body, { childList: true, subtree: true });
    })();
""".trimIndent()

private fun progressScript(resumeMs: Long) = """
    (() => {
      const resume = $resumeMs / 1000;
      const attached = new WeakSet();
      function observe(video) {
        if (attached.has(video)) return;
        attached.add(video);
        let last = 0;
        let resumed = false;
        function report(force) {
          if (!Number.isFinite(video.duration) || video.duration < 60) return;
          if (!resumed && video.currentTime < 3 && resume > 0 && resume < video.duration - 15) {
            resumed = true;
            video.currentTime = resume;
            return;
          }
          resumed = true;
          if (!force && Math.abs(video.currentTime - last) < 5) return;
          last = video.currentTime;
          DoramaProgress.postMessage(JSON.stringify({
            position: Math.round(video.currentTime * 1000),
            duration: Math.round(video.duration * 1000)
          }));
        }
        video.addEventListener('timeupdate', () => report(false));
        video.addEventListener('pause', () => report(true));
        video.addEventListener('ended', () => report(true));
      }
      function scan() { document.querySelectorAll('video').forEach(observe); }
      document.addEventListener('DOMContentLoaded', () => {
        scan();
        new MutationObserver(scan).observe(document.documentElement, { childList: true, subtree: true });
      });
    })();
""".trimIndent()
