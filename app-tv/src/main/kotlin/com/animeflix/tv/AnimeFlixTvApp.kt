package com.animeflix.tv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeflix.playback.profileViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animeflix.model.*
import com.animeflix.playback.*

@Composable
private fun TvAnimeCard(anime: Anime, info: EpisodeAvailability?, scheduleLabel: String? = null, newLabel: String? = null, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AsyncImage(anime.posterUrl, null, Modifier.width(76.dp).height(108.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(anime.title + if(anime.favorite) " ★" else "", style = MaterialTheme.typography.titleLarge)
                if(anime.favorite) Text(info?.badge ?: "Pendientes por comprobar", color = MaterialTheme.colorScheme.primary)
                newLabel?.let { Text(it, color = AnimeFlixColors.primary) }
                if(scheduleLabel != null) Text(scheduleLabel) else info?.let { Text(it.latestLabel) }
                Text(anime.synopsis, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AnimeFlixTvApp(catalog: CatalogViewModel = profileViewModel(), history: WatchHistoryViewModel = profileViewModel(), episodes: EpisodesViewModel = profileViewModel(), updates: LibraryUpdatesViewModel = profileViewModel()) {
    val state by catalog.state.collectAsStateWithLifecycle()
    val section by catalog.section.collectAsStateWithLifecycle()
    val query by catalog.query.collectAsStateWithLifecycle()
    val selectedId by catalog.selectedId.collectAsStateWithLifecycle()
    val hideCaughtUp by catalog.hideCaughtUp.collectAsStateWithLifecycle()
    val marks by history.entries.collectAsStateWithLifecycle()
    val historyError by history.error.collectAsStateWithLifecycle()
    val episodeState by episodes.state.collectAsStateWithLifecycle()
    val selected = state.items.find { it.id == selectedId }
    val availability by updates.statuses.collectAsStateWithLifecycle()
    val inventories by updates.inventories.collectAsStateWithLifecycle()
    val checking by updates.refreshing.collectAsStateWithLifecycle()
    val updateError by updates.error.collectAsStateWithLifecycle()
    val today = rememberScheduleToday()
    LibraryRefreshOnResume(updates, selected?.sourceUrl, section == "favorites")
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var hideWatched by rememberSaveable(selectedId) { mutableStateOf(false) }
    var group by rememberSaveable(selectedId) { mutableIntStateOf(0) }
    var bulkTarget by remember { mutableStateOf<Episode?>(null) }
    fun play(url: String) { context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", url)
        .putExtra("title", "Episodio ${url.substringAfterLast('/')}").putExtra("tvMode", true)) }
    val inventory = inventories.find { it.mediaUrl == selected?.sourceUrl?.trimEnd('/') }
    LaunchedEffect(selected?.sourceUrl, inventory) { selected?.let {
        if(inventory != null) episodes.useInventory(inventory) else episodes.load(it.sourceUrl)
    } }
    com.animeflix.playback.CatalogRefreshOnResume(section == "airing" && selectedId == null) { catalog.refreshSchedule() }
    LaunchedEffect(section, state.airing, state.watched, inventories) { if(section == "airing") updates.refreshScheduled(state.airing, state.watched) }
    LaunchedEffect(selectedId, section) { firstFocus.requestFocus() }
    BackHandler(enabled = selectedId != null || section != "airing") {
        if (selectedId != null) catalog.open(null) else catalog.navigate("airing")
    }
    androidx.compose.material3.MaterialTheme(colorScheme = AnimeFlixColors) {
    MaterialTheme(colorScheme = androidx.tv.material3.darkColorScheme(primary = AnimeFlixColors.primary, background = AnimeFlixColors.background, surface = AnimeFlixColors.surface)) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedId != null) Button(onClick = { catalog.open(null) }, modifier = Modifier.focusRequester(firstFocus)) { Text("Volver") }
                else {
                    Button(onClick = { catalog.navigate("airing") }, modifier = if(section == "airing") Modifier.focusRequester(firstFocus) else Modifier) { Text("En emisión") }
                    Button(onClick = { catalog.navigate("catalog") }, modifier = if(section == "catalog") Modifier.focusRequester(firstFocus) else Modifier) { Text("Catálogo") }
                    Button(onClick = { catalog.navigate("favorites") }, modifier = if(section == "favorites") Modifier.focusRequester(firstFocus) else Modifier) { Text("Mi lista") }
                    Button(onClick = { catalog.navigate("history") }, modifier = if(section == "history") Modifier.focusRequester(firstFocus) else Modifier) { Text("Historial") }
                    Button(onClick = { openDownloads(context) }) { Text("Descargas") }
                    Button(onClick = { draft = query; searchOpen = true }) { Text("Buscar") }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AnimeFlix TV", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if(selectedId == null && section != "history") Button(onClick = { catalog.setHideCaughtUp(!hideCaughtUp) },
                    modifier = Modifier.semantics { contentDescription = "Ocultar series al día"; stateDescription = if(hideCaughtUp) "Activado" else "Desactivado" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = hideCaughtUp, onCheckedChange = null)
                        Text("Ocultar al día")
                    }
                }
            }
            (state.error ?: historyError)?.let { message ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message, modifier = Modifier.weight(1f))
                    Button(onClick = catalog::retry) { Text("Reintentar") }
                }
            }
            when {
                section == "airing" && selectedId == null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("En emisión · Hoy primero", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = catalog::retry, enabled = !state.airingLoading) { Text("Actualizar horario") }
                    }
                    if(state.airingLoading || checking) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                    updateError?.let { Text(it) }
                    val matching = com.animeflix.data.orderAiring(state.airing, today).filter { it.anime.title.contains(query.trim(), true) }
                    val visible = matching.filter { !hideCaughtUp || !it.caughtUp(availability[it.anime.sourceUrl], state.watched) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                        if(visible.isEmpty() && !state.airingLoading) item { Text(if(matching.isNotEmpty()) "Todo al día. Las series reaparecerán al publicarse un capítulo pendiente." else "No hay emisiones para mostrar. Actualiza el horario o prueba otra búsqueda.") }
                        com.animeflix.data.scheduleDays(today).forEach { day ->
                            val rows = visible.filter { com.animeflix.data.scheduleDay(it.postedAt) == day }
                            if(rows.isNotEmpty()) {
                                item(key = "day-$day") { Text(com.animeflix.data.airingDayLabel(day, today), style = MaterialTheme.typography.titleLarge, color = AnimeFlixColors.primary) }
                                items(rows, key = { it.anime.id }) { row ->
                                    val anime = state.items.find { it.id == row.anime.id } ?: row.anime
                                    val status = availability[anime.sourceUrl]
                                    val newer = status != null && row.latestUrl !in state.watched && inventories.find { it.mediaUrl == anime.sourceUrl }?.episodes?.none { it.pageUrl == row.latestUrl } == true
                                    TvAnimeCard(anime, if(newer) null else status,
                                        "Último: episodio ${row.latestNumber} · " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(row.postedAt)),
                                        if(newer) "Nuevo episodio ${row.latestNumber}" else null) { catalog.open(anime.id) }
                                }
                            }
                        }
                    }
                }
                state.loading -> Text("Cargando catálogo…")
                selected != null -> {
                    val current = selected
                    val seen = marks.filter { it.mediaUrl == current.sourceUrl.trimEnd('/') }.associateBy { it.episodeUrl }
                    val last = seen.values.filter { it.openedAt > 0 }.maxByOrNull { it.openedAt }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                AsyncImage(current.posterUrl, null, Modifier.width(120.dp).height(170.dp), contentScale = ContentScale.Crop)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(current.title, style = MaterialTheme.typography.headlineMedium)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(availability[current.sourceUrl.trimEnd('/')]?.badge ?: "Pendientes por comprobar", modifier = Modifier.weight(1f))
                                        Button(onClick = { catalog.toggleFavorite(current) }) { Text(if(current.favorite) "Quitar de mi lista" else "Añadir a mi lista") }
                                    }
                                    last?.let { entry -> Button(onClick = { play(entry.episodeUrl) }) { Text("Continuar episodio ${entry.number}") } }
                                    availability[current.sourceUrl.trimEnd('/')]?.let { Text(it.latestLabel) }
                                    Button(onClick = { updates.check(current.sourceUrl, force = true) }, enabled = !checking) { Text("Actualizar episodios y fecha") }
                                    Text(current.synopsis)
                                }
                            }
                        }
                        when {
                            episodeState.mediaUrl != current.sourceUrl.trimEnd('/') || episodeState.loading -> item { Text("Cargando episodios…") }
                            episodeState.error -> item { Button(onClick = { episodes.load(current.sourceUrl, true) }) { Text("Reintentar episodios") } }
                            episodeState.items.isEmpty() -> item { Text("Este título todavía no tiene episodios publicados.") }
                            else -> {
                                val filtered = episodeState.items.filter { !hideWatched || seen[it.pageUrl]?.watched != true }
                                val page = group.coerceIn(0, ((filtered.size - 1) / 50).coerceAtLeast(0))
                                val visible = filtered.drop(page * 50).take(50)
                                item { Button(onClick = { hideWatched = !hideWatched; group = 0 }) { Text(if(hideWatched) "✓ Ocultar vistos" else "Ocultar vistos") } }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { group = page - 1 }, enabled = page > 0) { Text("Anteriores") }
                                        Text(if(visible.isEmpty()) "Todo visto. Desactiva el filtro." else "${visible.first().number} – ${visible.last().number} · ${filtered.size} episodios")
                                        Button(onClick = { group = page + 1 }, enabled = (page + 1) * 50 < filtered.size) { Text("Siguientes") }
                                    }
                                }
                                items(visible, key = { it.pageUrl }) { episode ->
                                    val watched = seen[episode.pageUrl]?.watched == true
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { play(episode.pageUrl) }, modifier = Modifier.weight(1f)) { Text("Ver episodio ${episode.number}") }
                                        DownloadEpisodeButton(episode.pageUrl, "Episodio ${episode.number}", compact = true)
                                        Button(onClick = { history.mark(episode.pageUrl, !watched) }, modifier = Modifier.semantics { contentDescription = if(watched) "Desmarcar como visto" else "Marcar visto" }) { EpisodeActionIcon(watched = watched) }
                                        Button(onClick = { bulkTarget = episode }, enabled = episode != episodeState.items.first(), modifier = Modifier.semantics { contentDescription = "Marcar anteriores como vistos" }) { EpisodeActionIcon(previous = true) }
                                    }
                                }
                            }
                        }
                    }
                }
                selectedId != null -> Text("El título no está disponible. Pulsa Volver.")
                section == "history" -> {
                    val opened = com.animeflix.data.latestHistoryPerSeries(marks)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("TU RECORRIDO", style = MaterialTheme.typography.labelMedium, color = AnimeFlixColors.primary)
                            Text("Historial", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text("Retoma tus historias, a tu ritmo.", color = AnimeFlixColors.onSurfaceVariant)
                        } }
                        if(opened.isEmpty()) item { Text("Todavía no has abierto episodios en este perfil.") }
                        itemsIndexed(opened, key = { _, entry -> entry.episodeUrl }) { index, entry ->
                            val anime = state.items.find { it.sourceUrl.trimEnd('/') == entry.mediaUrl }
                            val title = anime?.title ?: entry.mediaUrl.substringAfterLast('/').replace('-', ' ')
                            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = AnimeFlixColors.surface),
                                border = BorderStroke(1.dp, if(index == 0) AnimeFlixColors.primary.copy(alpha = .35f) else Color.White.copy(alpha = .07f))) {
                                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(anime?.posterUrl, null, Modifier.width(88.dp).height(128.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Episodio ${entry.number}" + if(entry.positionMs > 0) " · ${playbackTime(entry.positionMs)} guardados" else "",
                                            color = AnimeFlixColors.onSurfaceVariant)
                                        if(entry.durationMs > 0 && !entry.watched) androidx.compose.material3.LinearProgressIndicator(
                                            progress = { (entry.positionMs.toDouble() / entry.durationMs).coerceIn(0.0, 1.0).toFloat() },
                                            modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = Color.White.copy(alpha = .08f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Button(onClick = { play(entry.episodeUrl) }) { Text(if(entry.positionMs > 0 && !entry.watched) "Continuar" else "Abrir de nuevo") }
                                            Button(onClick = { history.watched(entry, !entry.watched) }) { Text(if(entry.watched) "Visto · desmarcar" else "Marcar visto") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val matching = state.items.filter { (section != "favorites" || it.favorite) &&
                        (it.title.contains(query.trim(), true) || it.synopsis.contains(query.trim(), true)) }
                    val visible = matching.filter { !hideCaughtUp || availability[it.sourceUrl.trimEnd('/')]?.caughtUp != true }
                    Text(if(section == "favorites") "Mi lista" else "Catálogo · Orden alfabético", style = MaterialTheme.typography.headlineMedium)
                    if(section == "favorites") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { updates.refreshFavorites(force = true) }, enabled = !checking) { Text(if(checking) "Comprobando episodios…" else "Actualizar episodios") }
                            Text("Pendientes según tus marcas de visto.")
                        }
                        updateError?.let { Text(it) }
                    }
                    if(query.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Búsqueda: $query")
                        Button(onClick = { catalog.search("") }) { Text("Borrar búsqueda") }
                    }
                    if(state.refreshing) Text("Actualizando…")
                    LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                        if(visible.isEmpty()) item { Text(if(matching.isNotEmpty() && hideCaughtUp) "Series al día ocultas. Desactiva Ocultar al día para volver a verlas."
                            else "No hay títulos para mostrar. Prueba otra búsqueda o añade favoritos.") }
                        items(visible, key = { it.id }) { anime ->
                            TvAnimeCard(anime, availability[anime.sourceUrl.trimEnd('/')]) { catalog.open(anime.id) }
                        }
                        if(state.canLoadMore && section == "catalog") item { Button(onClick = catalog::loadMore, enabled = !state.refreshing) { Text("Cargar más títulos") } }
                    }
                }
            }
        }
    }
    if(searchOpen) {
        val editFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { editFocus.requestFocus() }
        AlertDialog(onDismissRequest = { searchOpen = false }, title = { Text("Buscar anime") },
            text = { OutlinedTextField(draft, { draft = it }, modifier = Modifier.focusRequester(editFocus), singleLine = true,
                label = { Text("Título del anime") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { catalog.search(draft); searchOpen = false })) },
            confirmButton = { Button(onClick = { catalog.search(draft); searchOpen = false }) { Text("Buscar") } },
            dismissButton = { Button(onClick = { searchOpen = false }) { Text("Cancelar") } })
    }
    bulkTarget?.let { target ->
        val count = episodeState.items.count { it.number.toBigDecimal() < target.number.toBigDecimal() && marks.find { entry -> entry.episodeUrl == it.pageUrl }?.watched != true }
        AlertDialog(onDismissRequest = { bulkTarget = null }, title = { Text("Marcar anteriores como vistos") },
            text = { Text("Se marcarán $count episodios anteriores al ${target.number}. El seleccionado y los posteriores no cambiarán.") },
            confirmButton = { Button(onClick = { history.previous(episodeState.items, target); bulkTarget = null }) { Text("Marcar anteriores") } },
            dismissButton = { Button(onClick = { bulkTarget = null }) { Text("Cancelar") } })
    }
    }
    }
}
