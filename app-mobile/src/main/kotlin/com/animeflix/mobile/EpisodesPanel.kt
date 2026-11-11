package com.animeflix.mobile

import com.animeflix.playback.WatchHistoryViewModel
import com.animeflix.playback.EpisodesViewModel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.animeflix.playback.EpisodeActionIcon
import com.animeflix.playback.profileViewModel
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
import com.animeflix.model.playbackTime

@Composable
fun EpisodesPanel(mediaUrl: String, model: EpisodesViewModel = profileViewModel(), historyModel: WatchHistoryViewModel = profileViewModel(), inventory: EpisodeInventory? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val history by historyModel.entries.collectAsStateWithLifecycle()
    val historyError by historyModel.error.collectAsStateWithLifecycle()
    val watched = history.filter { it.mediaUrl == mediaUrl.trimEnd('/') }.associateBy { it.episodeUrl }
    val lastOpened = watched.values.filter { it.openedAt > 0 }.maxByOrNull { it.openedAt }
    var hideWatched by rememberSaveable(mediaUrl) { mutableStateOf(false) }
    var group by rememberSaveable(mediaUrl) { mutableIntStateOf(0) }
    var previousTarget by remember(mediaUrl) { mutableStateOf<Episode?>(null) }
    LaunchedEffect(mediaUrl, inventory) {
        if(inventory?.mediaUrl == mediaUrl.trimEnd('/')) model.useInventory(inventory) else model.load(mediaUrl)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
 Text("Episodios", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
 FilterChip(selected = hideWatched, onClick = { hideWatched = !hideWatched; group = 0 }, label = { Text("Ocultar vistos") })
}
    historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    lastOpened?.let { entry ->
        Button(onClick = { openEpisode(context, entry.episodeUrl, "Episodio ${entry.number}") }, modifier = Modifier.fillMaxWidth()) {
            Text("Retomar episodio ${entry.number}")
        }
        Text(if (entry.positionMs > 0) "Minuto guardado: ${playbackTime(entry.positionMs)}" else "Último abierto. Sin minuto guardado.", style = MaterialTheme.typography.bodySmall)
    }
    when {
        mediaUrl.isBlank() -> Text("No hay episodios en este título de demostración.")
        state.mediaUrl != mediaUrl.trimEnd('/') || state.loading -> CircularProgressIndicator()
        state.error -> {
            Text("No se pudieron cargar los episodios.")
            OutlinedButton(onClick = { model.load(mediaUrl, force = true) }) { Text("Reintentar episodios") }
        }
        state.items.isEmpty() -> Text("AnimeAV1 todavía no publica episodios para este título.")
        else -> {
            val filtered = state.items.filter { !hideWatched || watched[it.pageUrl]?.watched != true }
            val page = group.coerceIn(0, ((filtered.size - 1) / 50).coerceAtLeast(0))
            val visible = filtered.drop(page * 50).take(50)
            Text(if(visible.isEmpty()) "Todo visto. Desactiva el filtro para ver los episodios." else "${filtered.size} episodios · ${visible.first().number} – ${visible.last().number}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { group = page - 1 }, enabled = page > 0) { Text("Anteriores") }
                OutlinedButton(onClick = { group = page + 1 }, enabled = (page + 1) * 50 < filtered.size) { Text("Siguientes") }
            }
            visible.forEach { episode ->
                val seen = watched[episode.pageUrl]?.watched == true
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { openEpisode(context, episode.pageUrl, episode.title) }, modifier = Modifier.weight(1f)) { Text("Episodio ${episode.number}", maxLines = 1) }
                    com.animeflix.playback.DownloadEpisodeButton(episode.pageUrl, "Episodio ${episode.number}", compact = true)
                    IconToggleButton(checked = seen, onCheckedChange = { historyModel.mark(episode.pageUrl, it) }, modifier = Modifier.semantics { contentDescription = if(seen) "Desmarcar episodio ${episode.number} como visto" else "Marcar episodio ${episode.number} como visto" }) { EpisodeActionIcon(watched = seen) }
                    IconButton(onClick = { previousTarget = episode }, enabled = state.items.any { it.number.toBigDecimal() < episode.number.toBigDecimal() }, modifier = Modifier.semantics { contentDescription = "Marcar anteriores al episodio ${episode.number} como vistos" }) { EpisodeActionIcon(previous = true) }
                }
            }
        }
    }
    previousTarget?.let { target ->
        val count = state.items.count { it.number.toBigDecimal() < target.number.toBigDecimal() && watched[it.pageUrl]?.watched != true }
        AlertDialog(onDismissRequest = { previousTarget = null }, title = { Text("Marcar anteriores como vistos") },
            text = { Text("Se marcarán $count episodios anteriores al ${target.number}, incluidos los de otras páginas. El episodio ${target.number} no se cambiará.") },
            confirmButton = { TextButton(onClick = { historyModel.previous(state.items, target); previousTarget = null }) { Text("Marcar anteriores") } },
            dismissButton = { TextButton(onClick = { previousTarget = null }) { Text("Cancelar") } })
    }
}
