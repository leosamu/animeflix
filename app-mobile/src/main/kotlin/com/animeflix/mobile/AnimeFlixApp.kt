package com.animeflix.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeflix.playback.profileViewModel
import com.animeflix.model.Anime
import com.animeflix.model.EpisodeAvailability
import com.animeflix.model.EpisodeInventory
import com.animeflix.playback.LibraryUpdatesViewModel
import com.animeflix.playback.LibraryRefreshOnResume

private val colors = com.animeflix.playback.AnimeFlixColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeFlixApp(model: CatalogViewModel = profileViewModel(), updates: LibraryUpdatesViewModel = profileViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val section by model.section.collectAsStateWithLifecycle()
    val selectedId by model.selectedId.collectAsStateWithLifecycle()
    val hideCaughtUp by model.hideCaughtUp.collectAsStateWithLifecycle()
    val selected = state.items.find { it.id == selectedId }
    val availability by updates.statuses.collectAsStateWithLifecycle()
    val inventories by updates.inventories.collectAsStateWithLifecycle()
    val checking by updates.refreshing.collectAsStateWithLifecycle()
    val updateError by updates.error.collectAsStateWithLifecycle()
    LibraryRefreshOnResume(updates, selected?.sourceUrl, section == "favorites", !BuildConfig.DEMO_CATALOG)
    BackHandler(enabled = selectedId != null) { model.open(null) }
    BackHandler(enabled = selectedId == null && section != "catalog") { model.navigate("catalog") }
    MaterialTheme(colorScheme = colors) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("ANIMEFLIX", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = { if (selectedId != null) TextButton(onClick = { model.open(null) }) { Text("Volver") } },
                actions = { if(selectedId == null && section != "history") {
                    Row(Modifier.padding(end = 8.dp).toggleable(hideCaughtUp, role = Role.Switch, onValueChange = model::setHideCaughtUp)
                        .semantics { contentDescription = "Ocultar series al día" }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ocultar al día", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = hideCaughtUp, onCheckedChange = null)
                    }
                } })
        }, bottomBar = {
            if (selectedId == null) NavigationBar {
                NavigationBarItem(selected = section == "catalog", onClick = { model.navigate("catalog") }, icon = { Text("▦") }, label = { Text("Explorar") })
                NavigationBarItem(selected = section == "favorites", onClick = { model.navigate("favorites") }, icon = { Text("★") }, label = { Text("Mi lista") })
                NavigationBarItem(selected = section == "history", onClick = { model.navigate("history") }, icon = { Text("◷") }, label = { Text("Historial") })
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.error?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = model::retry) { Text("Reintentar") }
                        }
                    }
                }
                when {
                    state.loading || (state.items.isEmpty() && state.refreshing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    selected != null -> AnimeDetails(selected, availability[selected.sourceUrl.trimEnd('/')],
                        inventories.find { it.mediaUrl == selected.sourceUrl.trimEnd('/') },
                        onRefresh = { updates.check(selected.sourceUrl, force = true) }, onFavorite = { model.toggleFavorite(selected) })
                    selectedId != null -> EmptyMessage("Anime no disponible", "Vuelve al catálogo para elegir otro.")
                    section == "history" -> WatchHistoryPanel(state.items)
                    else -> {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(if (section == "favorites") "Mi lista" else "Tu próxima aventura", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            Text(if (section == "favorites") "Tus favoritos, siempre a mano." else "Descubre historias y guarda las que te interesen.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                            OutlinedTextField(value = query, onValueChange = model::search, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Buscar anime") }, trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { model.search("") }) { Text("Borrar") } })
                            Text(if (BuildConfig.DEMO_CATALOG) "Catálogo de demostración · Sin vídeo disponible" else "Catálogo de AnimeAV1 · Búsqueda en línea", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (!BuildConfig.DEMO_CATALOG && section == "catalog") TextButton(onClick = model::retry, enabled = !state.refreshing) { Text("Actualizar catálogo") }
                            if(section == "favorites") {
                                Text("Pendientes según tus marcas de visto.", style = MaterialTheme.typography.bodySmall)
                                if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                                updateError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                TextButton(onClick = { updates.refreshFavorites(force = true) }, enabled = !checking && !BuildConfig.DEMO_CATALOG) { Text("Actualizar episodios") }
                            }
                        }
                        val matching = state.items.filter { (section != "favorites" || it.favorite) &&
                            (it.title.contains(query.trim(), true) || it.synopsis.contains(query.trim(), true)) }
                        val visible = matching.filter { !hideCaughtUp || availability[it.sourceUrl.trimEnd('/')]?.caughtUp != true }
                        if(visible.isEmpty() && matching.isNotEmpty() && hideCaughtUp) EmptyMessage("Series al día ocultas", "Desactiva Ocultar al día para volver a verlas.")
                        else if (visible.isEmpty()) EmptyMessage(when { query.isNotBlank() -> "Sin resultados"; section == "favorites" -> "Tu lista empieza aquí"; else -> "Catálogo vacío" },
                            when { query.isNotBlank() -> "Prueba con otro título o borra la búsqueda."; section == "favorites" -> "Abre una ficha y añade tu primer favorito."; else -> "Actualiza el catálogo para descargar los primeros títulos." })
                        else LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(visible, key = { it.id }) { anime -> AnimeCard(anime, availability[anime.sourceUrl.trimEnd('/')], onClick = { model.open(anime.id) }) }
                            if (state.canLoadMore && section == "catalog") item(span = { GridItemSpan(maxLineSpan) }) {
                                OutlinedButton(onClick = model::loadMore, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
                            }
                        }
                        if(visible.isEmpty() && section == "catalog" && state.canLoadMore)
                            OutlinedButton(onClick = model::loadMore, enabled = !state.refreshing, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Cargar más") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(anime: Anime, availability: EpisodeAvailability?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
        Poster(anime, Modifier.fillMaxWidth().height(190.dp))
        Column(Modifier.padding(12.dp)) {
            Text(anime.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if(anime.favorite) Text(availability?.badge ?: "Pendientes por comprobar", color = if((availability?.pending ?: 0) > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            availability?.let { Text(it.latestLabel, style = MaterialTheme.typography.bodySmall) }
            Text(if (anime.favorite) "★ En mi lista" else "Ver ficha", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Poster(anime: Anime, modifier: Modifier = Modifier) {
    val palette = listOf(Color(0xFF6047A8), Color(0xFF286574), Color(0xFFA94C68), Color(0xFF466B4B))
    val index = ((anime.id.hashCode().toLong() and 0x7fffffff) % palette.size).toInt()
    Box(modifier.background(Brush.linearGradient(listOf(palette[index], Color(0xFF161B2D)))), contentAlignment = Alignment.Center) {
        Text(anime.title.take(1), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = .8f))
        if (anime.posterUrl.isNotBlank()) AsyncImage(model = anime.posterUrl, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (anime.sourceUrl.isBlank()) Text("DEMO", Modifier.align(Alignment.BottomStart).padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun AnimeDetails(anime: Anime, availability: EpisodeAvailability?, inventory: EpisodeInventory?, onRefresh: () -> Unit, onFavorite: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(shape = RoundedCornerShape(24.dp)) { Poster(anime, Modifier.fillMaxWidth().height(240.dp)) }
        Text(anime.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        availability?.let { Text(it.badge, color = MaterialTheme.colorScheme.primary); Text(it.latestLabel) }
        if(anime.sourceUrl.isNotBlank() && !BuildConfig.DEMO_CATALOG) TextButton(onClick = onRefresh) { Text("Actualizar episodios y fecha") }
        Button(onClick = onFavorite, modifier = Modifier.fillMaxWidth()) { Text(if (anime.favorite) "Quitar de mi lista" else "Añadir a mi lista") }
        Text("Sinopsis", style = MaterialTheme.typography.titleLarge)
        Text(anime.synopsis, style = MaterialTheme.typography.bodyLarge)
        if (anime.sourceUrl.isNotBlank()) {
            OutlinedButton(onClick = {
                try { uriHandler.openUri(anime.sourceUrl); linkError = false }
                catch (_: android.content.ActivityNotFoundException) { linkError = true }
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir ficha en AnimeAV1") }
            if (linkError) Text("No hay un navegador disponible para abrir la ficha.")
        }
        EpisodesPanel(anime.sourceUrl, inventory = inventory)
    }
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
