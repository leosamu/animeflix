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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
import com.animeflix.model.playbackTime

@Composable
fun EpisodesPanel(mediaUrl: String, model: EpisodesViewModel = viewModel(), historyModel: WatchHistoryViewModel = viewModel(), inventory: EpisodeInventory? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val history by historyModel.entries.collectAsStateWithLifecycle()
    val historyError by historyModel.error.collectAsStateWithLifecycle()
    val watched = history.filter { it.mediaUrl == mediaUrl.trimEnd('/') }.associateBy { it.episodeUrl }
    val lastOpened = watched.values.filter { it.openedAt > 0 }.maxByOrNull { it.openedAt }
    var group by rememberSaveable(mediaUrl) { mutableIntStateOf(0) }
    var previousTarget by remember(mediaUrl) { mutableStateOf<Episode?>(null) }
    LaunchedEffect(mediaUrl, inventory) {
        if(inventory?.mediaUrl == mediaUrl.trimEnd('/')) model.useInventory(inventory) else model.load(mediaUrl)
    }
    Text("Episodios", style = MaterialTheme.typography.titleLarge)
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
            val page = group.coerceIn(0, (state.items.size - 1) / 50)
            val visible = state.items.drop(page * 50).take(50)
            Text("${state.items.size} episodios · ${visible.first().number} – ${visible.last().number}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { group = page - 1 }, enabled = page > 0) { Text("Anteriores") }
                OutlinedButton(onClick = { group = page + 1 }, enabled = (page + 1) * 50 < state.items.size) { Text("Siguientes") }
            }
            visible.forEach { episode ->
                val seen = watched[episode.pageUrl]?.watched == true
                Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    openEpisode(context, episode.pageUrl, episode.title)
                }, modifier = Modifier.weight(1f)) { Text("Ver ${episode.title.lowercase()}") }
                FilterChip(selected = seen, onClick = { historyModel.mark(episode.pageUrl, !seen) },
                    label = { Text(if (seen) "Visto ✓" else "Marcar visto") })
                }
                if (state.items.any { it.number.toBigDecimal() < episode.number.toBigDecimal() }) TextButton(onClick = { previousTarget = episode }) {
                    Text("Marcar anteriores al ${episode.number} como vistos")
                }
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
