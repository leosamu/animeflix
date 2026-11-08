package com.animeflix.mobile

import com.animeflix.playback.PlayerActivity
import com.animeflix.playback.WatchHistoryViewModel

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animeflix.model.Anime
import com.animeflix.model.playbackTime
import java.text.DateFormat
import java.util.Date

internal fun openEpisode(context: Context, url: String, title: String) {
    context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", url).putExtra("title", title))
}

@Composable
fun WatchHistoryPanel(catalog: List<Anime>, model: WatchHistoryViewModel = viewModel()) {
    val entries by model.entries.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val opened = entries.filter { it.openedAt > 0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Historial", style = MaterialTheme.typography.headlineMedium)
            Text("Vuelve al último episodio que abriste.")
            Text("Se guarda el minuto en reproductores compatibles.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (opened.isEmpty()) item { Text("Todavía no has abierto ningún episodio.") }
        items(opened, key = { it.episodeUrl }) { entry ->
            val anime = catalog.find { it.sourceUrl.trimEnd('/') == entry.mediaUrl }
            val title = anime?.title ?: entry.mediaUrl.substringAfterLast('/').replace('-', ' ')
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("Episodio ${entry.number}")
                    if (entry.positionMs > 0) Text("Minuto guardado: ${playbackTime(entry.positionMs)}")
                    Text("Abierto: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.openedAt)),
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { openEpisode(context, entry.episodeUrl, "Episodio ${entry.number}") }) { Text("Abrir de nuevo") }
                    FilterChip(selected = entry.watched, onClick = { model.watched(entry, !entry.watched) },
                        label = { Text(if (entry.watched) "Visto · desmarcar" else "Marcar como visto") })
                }
            }
        }
    }
}
