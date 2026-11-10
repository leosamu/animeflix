package com.animeflix.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animeflix.model.Anime
import com.animeflix.model.WatchEntry
import com.animeflix.model.playbackTime
import com.animeflix.playback.PlayMark
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryContent(catalog: List<Anime>, entries: List<WatchEntry>, error: String?,
    onPlay: (WatchEntry) -> Unit, onWatched: (WatchEntry) -> Unit) {
    val opened = entries.filter { it.openedAt > 0 }.sortedByDescending { it.openedAt }
    val resume = opened.firstOrNull { !it.watched && it.positionMs > 0 && it.durationMs > it.positionMs }
    val animeByUrl = catalog.associateBy { it.sourceUrl.trimEnd('/') }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                Text("TU RECORRIDO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Historial", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("Las historias a las que siempre puedes volver.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (resume != null) item {
            ContinueWatchingCard(resume, animeByUrl[resume.mediaUrl], onPlay = { onPlay(resume) })
        }
        if (opened.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .06f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayMark(Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                    Text("Tu próxima historia empieza aquí", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Todavía no has abierto ningún episodio. Cuando lo hagas, podrás volver a él desde aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Actividad reciente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                Text("${opened.size} ${if (opened.size == 1) "episodio" else "episodios"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(opened, key = { _, entry -> entry.episodeUrl }) { index, entry ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val day = historyDay(entry.openedAt)
                if (index == 0 || day != historyDay(opened[index - 1].openedAt)) {
                    Text(day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                HistoryEpisodeCard(entry, animeByUrl[entry.mediaUrl], onPlay = { onPlay(entry) }, onWatched = { onWatched(entry) })
            }
        }
        if (opened.isNotEmpty()) item {
            Text("El historial muestra los episodios abiertos. Tú decides cuáles marcar como vistos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun ContinueWatchingCard(entry: WatchEntry, anime: Anime?, onPlay: () -> Unit) {
    Card(onClick = onPlay, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
            if (!anime?.posterUrl.isNullOrBlank()) AsyncImage(anime?.posterUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xFF131726), Color(0xFF131726).copy(alpha = .9f), Color(0xFF131726).copy(alpha = .55f)))))
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SEGUIR VIENDO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(historyTitle(anime, entry), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Episodio ${entry.number} · ${playbackTime(entry.positionMs)} guardados", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD0D3DF))
                LinearProgressIndicator(progress = { historyProgress(entry) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)), trackColor = Color.White.copy(alpha = .15f))
                Button(onClick = onPlay, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                    PlayMark(); Spacer(Modifier.width(10.dp)); Text("Continuar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryEpisodeCard(entry: WatchEntry, anime: Anime?, onPlay: () -> Unit, onWatched: () -> Unit) {
    Card(onClick = onPlay, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .055f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(86.dp).height(126.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF5B3B63), Color(0xFF242B40)))), contentAlignment = Alignment.Center) {
                    Text(historyTitle(anime, entry).take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .6f))
                    if (!anime?.posterUrl.isNullOrBlank()) AsyncImage(anime?.posterUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(historyTitle(anime, entry), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Episodio ${entry.number}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.watched) Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) {
                        Text("VISTO", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    } else if (entry.positionMs > 0) {
                        Text("Minuto guardado: ${playbackTime(entry.positionMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (entry.durationMs > 0) LinearProgressIndicator(progress = { historyProgress(entry) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)), trackColor = Color.White.copy(alpha = .08f))
                    }
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.openedAt)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 4.dp), color = Color.White.copy(alpha = .06f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPlay, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    PlayMark(Modifier.size(13.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("Abrir de nuevo", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onWatched, contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (entry.watched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(if (entry.watched) "Visto · desmarcar" else "Marcar como visto", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun historyTitle(anime: Anime?, entry: WatchEntry) = anime?.title ?: entry.mediaUrl.substringAfterLast('/').replace('-', ' ')
private fun historyProgress(entry: WatchEntry) = if (entry.durationMs > 0) (entry.positionMs.toDouble() / entry.durationMs).coerceIn(0.0, 1.0).toFloat() else 0f
private fun historyDay(time: Long): String {
    val current = Calendar.getInstance()
    val opened = Calendar.getInstance().apply { timeInMillis = time }
    fun sameDay() = current.get(Calendar.YEAR) == opened.get(Calendar.YEAR) && current.get(Calendar.DAY_OF_YEAR) == opened.get(Calendar.DAY_OF_YEAR)
    if (sameDay()) return "HOY"
    current.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay()) return "AYER"
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time)).uppercase(Locale.getDefault())
}
