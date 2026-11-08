package com.animeflix.tv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.animeflix.model.*
import com.animeflix.playback.*

@Composable
fun AnimeFlixTvApp(catalog: CatalogViewModel = viewModel(), history: WatchHistoryViewModel = viewModel(), episodes: EpisodesViewModel = viewModel(), updates: LibraryUpdatesViewModel = viewModel()) {
    val state by catalog.state.collectAsStateWithLifecycle()
    val section by catalog.section.collectAsStateWithLifecycle()
    val query by catalog.query.collectAsStateWithLifecycle()
    val selectedId by catalog.selectedId.collectAsStateWithLifecycle()
    val marks by history.entries.collectAsStateWithLifecycle()
    val historyError by history.error.collectAsStateWithLifecycle()
    val episodeState by episodes.state.collectAsStateWithLifecycle()
    val selected = state.items.find { it.id == selectedId }
    val availability by updates.statuses.collectAsStateWithLifecycle()
    val inventories by updates.inventories.collectAsStateWithLifecycle()
    val checking by updates.refreshing.collectAsStateWithLifecycle()
    val updateError by updates.error.collectAsStateWithLifecycle()
    LibraryRefreshOnResume(updates, selected?.sourceUrl, section == "favorites")
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable(selectedId) { mutableIntStateOf(0) }
    var bulkTarget by remember { mutableStateOf<Episode?>(null) }
    fun play(url: String) { context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", url)
        .putExtra("title", "Episodio ${url.substringAfterLast('/')}").putExtra("tvMode", true)) }
    val inventory = inventories.find { it.mediaUrl == selected?.sourceUrl?.trimEnd('/') }
    LaunchedEffect(selected?.sourceUrl, inventory) { selected?.let {
        if(inventory != null) episodes.useInventory(inventory) else episodes.load(it.sourceUrl)
    } }
    LaunchedEffect(selectedId, section) { firstFocus.requestFocus() }
    BackHandler(enabled = selectedId != null || section != "catalog") {
        if (selectedId != null) catalog.open(null) else catalog.navigate("catalog")
    }
    androidx.compose.material3.MaterialTheme(colorScheme = darkColorScheme()) {
    MaterialTheme {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedId != null) Button(onClick = { catalog.open(null) }, modifier = Modifier.focusRequester(firstFocus)) { Text("Volver") }
                else {
                    Button(onClick = { catalog.navigate("catalog") }, modifier = Modifier.focusRequester(firstFocus)) { Text("Explorar") }
                    Button(onClick = { catalog.navigate("favorites") }) { Text("Mi lista") }
                    Button(onClick = { catalog.navigate("history") }) { Text("Historial") }
                    Button(onClick = { draft = query; searchOpen = true }) { Text("Buscar") }
                }
                Text("AnimeFlix TV", style = MaterialTheme.typography.titleLarge)
            }
            (state.error ?: historyError)?.let { message ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(message, modifier = Modifier.weight(1f))
                    Button(onClick = catalog::retry) { Text("Reintentar") }
                }
            }
            when {
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
                                    availability[current.sourceUrl.trimEnd('/')]?.let { Text(it.badge); Text(it.latestLabel) }
                                    Button(onClick = { updates.check(current.sourceUrl, force = true) }, enabled = !checking) { Text("Actualizar episodios y fecha") }
                                    Text(current.synopsis)
                                    Button(onClick = { catalog.toggleFavorite(current) }) { Text(if (current.favorite) "Quitar de mi lista" else "Añadir a mi lista") }
                                }
                            }
                        }
                        last?.let { entry -> item {
                            Button(onClick = { play(entry.episodeUrl) }) { Text("Retomar episodio ${entry.number}" + if(entry.positionMs > 0) " · ${playbackTime(entry.positionMs)}" else "") }
                        } }
                        when {
                            episodeState.mediaUrl != current.sourceUrl.trimEnd('/') || episodeState.loading -> item { Text("Cargando episodios…") }
                            episodeState.error -> item { Button(onClick = { episodes.load(current.sourceUrl, true) }) { Text("Reintentar episodios") } }
                            episodeState.items.isEmpty() -> item { Text("Este título todavía no tiene episodios publicados.") }
                            else -> {
                                val page = group.coerceIn(0, (episodeState.items.size - 1) / 50)
                                val visible = episodeState.items.drop(page * 50).take(50)
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { group = page - 1 }, enabled = page > 0) { Text("Anteriores") }
                                        Text("${visible.first().number} – ${visible.last().number} · ${episodeState.items.size} episodios")
                                        Button(onClick = { group = page + 1 }, enabled = (page + 1) * 50 < episodeState.items.size) { Text("Siguientes") }
                                    }
                                }
                                items(visible, key = { it.pageUrl }) { episode ->
                                    val watched = seen[episode.pageUrl]?.watched == true
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { play(episode.pageUrl) }, modifier = Modifier.weight(1f)) { Text("Ver episodio ${episode.number}") }
                                        Button(onClick = { history.mark(episode.pageUrl, !watched) }) { Text(if(watched) "Visto ✓ · desmarcar" else "Marcar visto") }
                                        Button(onClick = { bulkTarget = episode }, enabled = episode != episodeState.items.first()) { Text("Anteriores vistos") }
                                    }
                                }
                            }
                        }
                    }
                }
                selectedId != null -> Text("El título no está disponible. Pulsa Volver.")
                section == "history" -> {
                    val opened = marks.filter { it.openedAt > 0 }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
                        item { Text("Historial", style = MaterialTheme.typography.headlineMedium) }
                        if(opened.isEmpty()) item { Text("Todavía no has abierto episodios en este dispositivo.") }
                        items(opened, key = { it.episodeUrl }) { entry ->
                            val title = state.items.find { it.sourceUrl == entry.mediaUrl }?.title ?: entry.mediaUrl.substringAfterLast('/').replace('-', ' ')
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { play(entry.episodeUrl) }, modifier = Modifier.weight(1f)) {
                                    Text("$title · Episodio ${entry.number}" + if(entry.positionMs > 0) " · ${playbackTime(entry.positionMs)}" else "")
                                }
                                Button(onClick = { history.watched(entry, !entry.watched) }) { Text(if(entry.watched) "Visto · desmarcar" else "Marcar visto") }
                            }
                        }
                    }
                }
                else -> {
                    val visible = state.items.filter { (section != "favorites" || it.favorite) &&
                        (it.title.contains(query.trim(), true) || it.synopsis.contains(query.trim(), true)) }
                    Text(if(section == "favorites") "Mi lista" else "Explorar AnimeAV1", style = MaterialTheme.typography.headlineMedium)
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
                        if(visible.isEmpty()) item { Text("No hay títulos para mostrar. Prueba otra búsqueda o añade favoritos.") }
                        items(visible, key = { it.id }) { anime ->
                            Surface(onClick = { catalog.open(anime.id) }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    AsyncImage(anime.posterUrl, null, Modifier.width(76.dp).height(108.dp), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f)) {
                                        Text(anime.title + if(anime.favorite) " ★" else "", style = MaterialTheme.typography.titleLarge)
                                        val info = availability[anime.sourceUrl.trimEnd('/')]
                                        if(anime.favorite) Text(info?.badge ?: "Pendientes por comprobar", color = MaterialTheme.colorScheme.primary)
                                        info?.let { Text(it.latestLabel) }
                                        Text(anime.synopsis, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
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
