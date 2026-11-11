package com.animeflix.playback

import android.view.KeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.animeflix.model.PlaybackServer
import com.animeflix.model.playbackTime

/** Native video stays mounted while the remote-controlled server menu is open. */
@Composable
internal fun TvNativePlayerLayout(title: String, episodeUrl: String, servers: List<PlaybackServer>, selected: PlaybackServer?,
    loading: Boolean, error: String?, position: Long, watched: Boolean, menu: Boolean, hasVideo: Boolean,
    onMenu: (Boolean) -> Unit, onSelect: (PlaybackServer) -> Unit, onRetry: () -> Unit, onMark: () -> Unit,
    onBack: () -> Unit, onWeb: () -> Unit, preparation: @Composable () -> Unit, player: @Composable () -> Unit) {
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(menu, selected?.embedUrl) {
        if(menu) { inputMode.requestInputMode(InputMode.Keyboard); withFrameNanos { }; first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
        if(it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
            if(it.nativeKeyEvent.action == KeyEvent.ACTION_UP) onMenu(true)
            true
        } else false
    }) {
        player()
        if(menu) {
            Column(Modifier.fillMaxHeight().padding(start = 28.dp, top = 28.dp, end = 350.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.Center) {
                if(!hasVideo) preparation()
            }
            Column(Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxHeight()
                .clip(RoundedCornerShape(20.dp)).background(AnimeFlixColors.surface.copy(alpha = 0.97f)).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("ANIMEFLIX · REPRODUCTOR", style = MaterialTheme.typography.labelMedium, color = AnimeFlixColors.primary)
                if(hasVideo) Button(onClick = { onMenu(false) }, modifier = Modifier.fillMaxWidth().tvRemoteFocus()) { Text("Ver vídeo · cerrar menú") }
                Text("Vídeo: cruceta y OK para los controles. Atrás o Menú: servidores.", style = MaterialTheme.typography.bodySmall)
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it) }
                selected?.takeIf { com.animeflix.data.Mp4UploadResolver.supports(it.embedUrl) }?.let { DownloadEpisodeButton(episodeUrl, title, it) }
                Text("Servidor", style = MaterialTheme.typography.titleSmall)
                servers.forEach { server ->
                    FilledTonalButton(onClick = { onSelect(server) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if(server == selected) AnimeFlixColors.primaryContainer else AnimeFlixColors.surfaceVariant), modifier = Modifier.fillMaxWidth().tvRemoteFocus()
                        .then(if(server == selected) Modifier.focusRequester(first) else Modifier)) {
                        Text(server.name + if(server == selected) " ✓" else "")
                    }
                }
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().tvRemoteFocus()
                    .then(if(selected == null) Modifier.focusRequester(first) else Modifier)) { Text("Reintentar servidor") }
                if(position > 0) Text("Continuar en ${playbackTime(position)}", style = MaterialTheme.typography.bodySmall, color = AnimeFlixColors.onSurfaceVariant)
                Button(onClick = onMark, modifier = Modifier.fillMaxWidth().tvRemoteFocus()) { Text(if(watched) "Visto · desmarcar" else "Marcar como visto") }
                Text("HLS sigue en pruebas. Byse puede pedir verificación manual.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onWeb, modifier = Modifier.fillMaxWidth().tvRemoteFocus()) { Text("Usar alternativa web") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().tvRemoteFocus()) { Text("Volver a los episodios") }
            }
        }
    }
}

@Composable
internal fun Modifier.tvRemoteFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focused = it.hasFocus }.border(3.dp,
        if(focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
}
