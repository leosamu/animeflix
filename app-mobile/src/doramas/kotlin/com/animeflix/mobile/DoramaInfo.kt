package com.animeflix.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animeflix.data.Dorama
import com.animeflix.data.DoramaDetails
import com.animeflix.data.DoramasCatalog
import com.animeflix.playback.tvRemoteFocus
import kotlinx.coroutines.CancellationException

@Composable
internal fun DoramaInfo(info: DoramaDetails) {
    val summary = listOfNotNull(
        info.year.takeIf(String::isNotBlank), info.country.takeIf(String::isNotBlank),
        info.runtime?.let { "$it min por episodio" }, info.episodeCount?.let { "$it episodios" },
        info.status.takeIf(String::isNotBlank)
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (info.show.poster.isNotBlank()) {
                    AsyncImage(info.show.poster, null, Modifier.size(100.dp, 150.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.isNotEmpty()) Text(summary.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                    if (info.synopsis.isNotBlank()) {
                        Text("Sinopsis", style = MaterialTheme.typography.titleMedium)
                        Text(info.synopsis)
                    } else Text("Sinopsis no disponible.")
                }
            }
        }
        val groups = listOf(
            "Géneros" to info.genres, "Etiquetas" to info.labels, "Productoras" to info.networks,
            "Audio" to info.audio, "Subtítulos" to info.subtitles
        )
        groups.filter { it.second.isNotEmpty() }.forEach { (label, values) ->
            item {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(values.joinToString(" · "))
            }
        }
        if (info.cast.isNotEmpty()) item {
            Text("Reparto", style = MaterialTheme.typography.titleMedium)
            info.cast.forEach { person ->
                Text(if (person.character.isBlank()) person.name else "${person.name} · ${person.character}")
            }
        }
    }
}

@Composable
internal fun DoramaSimilar(info: DoramaDetails, tv: Boolean, onOpen: (Dorama) -> Unit) {
    var attempt by remember(info.show.slug) { mutableIntStateOf(0) }
    var results by remember(info.show.slug) { mutableStateOf<List<Dorama>?>(null) }
    var failed by remember(info.show.slug) { mutableStateOf(false) }
    LaunchedEffect(info.show.slug, attempt) {
        failed = false
        try {
            results = DoramasCatalog.similar(info)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failed = true
        }
    }
    when {
        failed -> TextButton(onClick = { attempt++ }) { Text("Reintentar series similares") }
        results == null -> CircularProgressIndicator(Modifier.padding(16.dp))
        results.orEmpty().isEmpty() -> Text("Doramasflix no ofrece series similares para este título.", Modifier.padding(16.dp))
        else -> LazyColumn {
            items(results.orEmpty(), key = { it.slug }) { show ->
                ListItem(headlineContent = { Text(show.title) }, leadingContent = if (show.poster.isNotBlank()) ({
                    AsyncImage(show.poster, null, Modifier.size(52.dp, 76.dp), contentScale = ContentScale.Crop)
                }) else null, modifier = (if (tv) Modifier.tvRemoteFocus() else Modifier).clickable { onOpen(show) })
            }
        }
    }
}
