package com.animeflix.mobile

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animeflix.data.Dorama
import com.animeflix.data.DoramaAvailability
import com.animeflix.data.DoramaEpisode
import com.animeflix.data.DoramaFilter
import com.animeflix.data.DoramaFilterOption
import com.animeflix.data.DoramaPublication
import com.animeflix.data.DoramasCatalog
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.data.ProfileEntry
import com.animeflix.playback.LocalProfileViewModelKey
import com.animeflix.playback.LocalProfileSwitchFocus
import com.animeflix.playback.tvRemoteFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryTab(val label: String) {
    Recent("Últimos"), Catalog("Catálogo"), Favorites("Mi lista"), History("Historial"), Downloads("Descargas")
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun ProductApp() {
    val context = LocalContext.current
    val tv = remember(context) {
        context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val switchProfile = LocalProfileSwitchFocus.current
    val imeVisible = WindowInsets.isImeVisible
    val navigationFocus = remember { FocusRequester() }
    val repository = remember { ProfileAnimeRepository.create(context) }
    val entries by repository.observeDoramasEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val profile = LocalProfileViewModelKey.current
    val downloads = remember(context, profile) { DoramaDownloads(context.applicationContext, profile) }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Catalog) }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(emptyMap<DoramaFilter, DoramaFilterOption>()) }
    var showFilters by remember { mutableStateOf(false) }
    var catalogJob by remember { mutableStateOf<Job?>(null) }
    val searchTerm = query.trim()
    val linkedSlug = searchTerm.takeIf { it.startsWith(DoramasCatalog.ORIGIN + "/") }?.let(DoramasCatalog::series)
    val remoteSearch = tab == LibraryTab.Catalog && searchTerm.isNotEmpty() && linkedSlug == null
    val searchKey = searchTerm to filters
    var searchResult by remember { mutableStateOf<Pair<Pair<String, Map<DoramaFilter, DoramaFilterOption>>, List<Dorama>>?>(null) }
    var searchError by remember { mutableStateOf(false) }
    var searchRetry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Dorama?>(null) }
    var playing by remember { mutableStateOf<DoramaEpisode?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var offline by remember { mutableStateOf<DoramaDownloads.Entry?>(null) }
    var downloadOffer by remember { mutableStateOf<DoramaDownloadOffer?>(null) }
    val catalog = remember { mutableStateListOf<Dorama>() }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<DoramaEpisode>?>(null) }
    var detailError by remember { mutableStateOf(false) }
    var detailRetry by remember { mutableIntStateOf(0) }
    var hideWatched by rememberSaveable { mutableStateOf(false) }
    var previousTarget by remember { mutableStateOf<DoramaEpisode?>(null) }
    var marking by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<DoramaPublication>?>(null) }
    var recentError by remember { mutableStateOf(false) }
    var recentRetry by remember { mutableIntStateOf(0) }

    fun nextPage() {
        if (loading || !hasMore) return
        loading = true
        error = null
        catalogJob = scope.launch {
            try {
                val next = DoramasCatalog.catalog(page, filters)
                val existing = catalog.mapTo(mutableSetOf()) { it.slug }
                val additions = next.filter { it.slug !in existing }
                catalog.addAll(additions)
                hasMore = additions.isNotEmpty()
                page++
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = "No se pudo cargar el catálogo."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(filters) {
        catalogJob?.cancel()
        catalogJob?.join()
        catalog.clear()
        page = 1
        hasMore = true
        nextPage()
    }
    LaunchedEffect(remoteSearch, searchKey, searchRetry) {
        if (!remoteSearch) return@LaunchedEffect
        searchError = false
        delay(300)
        try {
            searchResult = searchKey to DoramasCatalog.search(searchTerm, filters)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            searchError = true
        }
    }
    LaunchedEffect(tab, recentRetry) {
        if (tab != LibraryTab.Recent || recent != null) return@LaunchedEffect
        recentError = false
        try {
            recent = DoramasCatalog.recent()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            recentError = true
        }
    }
    LaunchedEffect(selected, detailRetry) {
        val dorama = selected ?: return@LaunchedEffect
        episodes = null
        detailError = false
        try {
            episodes = DoramasCatalog.episodes(dorama)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            detailError = true
        }
    }

    val favorites = entries.filter { it.kind == "favorite" && it.json()?.optBoolean("enabled") == true }
        .mapNotNull { row ->
            val slug = DoramasCatalog.series(row.itemKey) ?: return@mapNotNull null
            val data = row.json() ?: return@mapNotNull null
            Dorama(slug, data.optString("title"), data.optString("posterUrl"))
        }.associateBy { it.slug }
    val history = entries.filter { it.kind == "opened" }.sortedByDescending { it.json()?.optLong("openedAt") }
    val progress = entries.filter { it.kind == "progress" }.associateBy { it.itemKey }
    val watched = entries.filter { it.kind == "watched" && it.json()?.optBoolean("watched") == true }
        .mapTo(mutableSetOf()) { it.itemKey }
    val watchedSlugs = watched.mapNotNull { DoramasCatalog.episode(it)?.slug }.toSet()
    val hideCaughtUp = entries.find { it.kind == "preference" && it.itemKey == "hideCaughtUp" }
        ?.json()?.optBoolean("enabled") == true

    fun play(episode: DoramaEpisode, title: String) {
        scope.launch { repository.recordDoramaOpened(episode, title) }
        playingTitle = title
        playing = episode
    }

    fun reportProgress(episode: DoramaEpisode, position: Long, duration: Long) {
        if (duration !in 60_000..86_400_000 || position !in 0..duration) return
        scope.launch {
            repository.saveDoramaProgress(episode, position, duration)
            if (position >= duration - 1_000 && episode.url !in watched) repository.markDoramaWatched(episode, true)
        }
    }

    offline?.let { entry ->
        key(entry.id) {
            val resume = remember { progress[entry.episode.url]?.json()?.optLong("positionMs") ?: 0L }
            DoramaOfflinePlayer(entry, resume, { position, duration -> reportProgress(entry.episode, position, duration) }) {
                offline = null
            }
        }
        return
    }

    val activeEpisode = playing
    if (activeEpisode != null) {
        key(activeEpisode) {
            val resume = remember { progress[activeEpisode.url]?.json()?.optLong("positionMs") ?: 0L }
            DoramasPlayer(activeEpisode, playingTitle, resume, activeEpisode.url in watched,
                onMark = { scope.launch { repository.markDoramaWatched(activeEpisode, activeEpisode.url !in watched) } },
                onProgress = { position, duration ->
                reportProgress(activeEpisode, position, duration)
            }, onBack = { playing = null }, onDownload = { downloadOffer = it })
            downloadOffer?.let { offer ->
                AlertDialog(
                    onDismissRequest = { downloadOffer = null },
                    title = { Text("Descargar episodio") },
                    text = { Text("$playingTitle · T${activeEpisode.season} E${activeEpisode.number}") },
                    dismissButton = { TextButton(onClick = { downloadOffer = null }) { Text("Cancelar") } },
                    confirmButton = { TextButton(onClick = {
                        downloadOffer = null
                        runCatching { downloads.enqueue(offer, activeEpisode, playingTitle) }.onFailure {
                            android.widget.Toast.makeText(context, "No se pudo iniciar la descarga.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Descargar") } }
                )
            }
        }
        return
    }

    val dorama = selected
    BackHandler(enabled = dorama != null) { selected = null }
    previousTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { previousTarget = null },
            title = { Text("Marcar anteriores como vistos") },
            text = { Text("Se marcarán los capítulos publicados anteriores a T${target.season} · E${target.number}.") },
            dismissButton = { TextButton(onClick = { previousTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                TextButton(onClick = {
                    val prior = episodes.orEmpty().takeWhile { it != target }.filter { it.url !in watched }
                    previousTarget = null
                    marking = true
                    scope.launch {
                        try {
                            val published = coroutineScope {
                                prior.map { earlier -> async {
                                    earlier.takeIf { available(it).audio.isNotEmpty() }
                                } }.awaitAll().filterNotNull()
                            }
                            repository.markDoramaPrevious(published)
                            android.widget.Toast.makeText(context, "${published.size} episodios marcados",
                                android.widget.Toast.LENGTH_SHORT).show()
                        } finally {
                            marking = false
                        }
                    }
                }) { Text("Marcar") }
            }
        )
    }
    Column(Modifier.fillMaxSize()) {
        if (dorama != null) {
            TextButton(onClick = { selected = null }) { Text("Volver") }
            Text(dorama.title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = {
                scope.launch { repository.setDoramaFavorite(dorama, dorama.slug !in favorites) }
            }) { Text(if (dorama.slug in favorites) "Quitar de Mi lista" else "Añadir a Mi lista") }
            val list = episodes
            val resume = list.orEmpty().firstOrNull { it.url == history.firstOrNull { row ->
                row.itemKey !in watched && row.itemKey in progress && DoramasCatalog.episode(row.itemKey)?.slug == dorama.slug
            }?.itemKey }
            val next by produceState<DoramaEpisode?>(null, list, watched, resume) {
                value = resume ?: list.orEmpty().asSequence().filter { it.url !in watched }
                    .firstOrNull { available(it).audio.isNotEmpty() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                next?.let { episode ->
                    Button(onClick = { play(episode, dorama.title) }, modifier = Modifier.padding(start = 12.dp)) {
                        Text("Continuar · T${episode.season} E${episode.number}")
                    }
                }
                TextButton(onClick = { hideWatched = !hideWatched }) {
                    Text(if (hideWatched) "Mostrar vistos" else "Ocultar vistos")
                }
            }
            if (marking) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                detailError -> TextButton(onClick = { detailRetry++ }) { Text("Reintentar episodios") }
                list == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                list.isEmpty() -> Text("No hay episodios disponibles.", Modifier.padding(16.dp))
                else -> LazyColumn {
                    list.filterNot { hideWatched && it.url in watched }.groupBy { it.season }.forEach { (season, seasonEpisodes) ->
                        item { Text("Temporada $season", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                        items(seasonEpisodes, key = { it.url }) { episode ->
                            val position = progress[episode.url]?.json()?.optLong("positionMs") ?: 0
                            val availability = episodeAvailability(episode)
                            ListItem(
                                headlineContent = { Text("Episodio ${episode.number}") },
                                supportingContent = {
                                    Column {
                                        Text(availability.label(), color = if (availability?.upcoming == true)
                                            MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (position > 0) Text("Continuar desde ${clock(position)}")
                                    }
                                },
                                trailingContent = {
                                    var actions by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { actions = true }, enabled = availability.canPlay()) {
                                            Text(if (episode.url in watched) "Visto · ⋮" else "⋮")
                                        }
                                        DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                                            DropdownMenuItem(text = { Text(if (episode.url in watched) "Marcar como no visto" else "Marcar como visto") },
                                                onClick = {
                                                    actions = false
                                                    scope.launch { repository.markDoramaWatched(episode, episode.url !in watched) }
                                                })
                                            if (episode != list.first()) DropdownMenuItem(
                                                text = { Text("Marcar anteriores como vistos") }, enabled = !marking,
                                                onClick = { actions = false; previousTarget = episode })
                                        }
                                    }
                                },
                                modifier = Modifier.clickable(enabled = availability.canPlay()) { play(episode, dorama.title) }
                            )
                        }
                    }
                }
            }
        } else {
            if (tab == LibraryTab.Catalog || tab == LibraryTab.Favorites)
                TextButton(onClick = { scope.launch { repository.setDoramaHideCaughtUp(!hideCaughtUp) } }) {
                    Text(if (hideCaughtUp) "Mostrar al día" else "Ocultar al día")
                }
            if (tab == LibraryTab.Catalog) {
                if (showFilters) DoramaFilterDialog(filters, onDismiss = { showFilters = false }) {
                    filters = it
                    showFilters = false
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp).focusProperties {
                    if (tv && switchProfile != null) up = switchProfile
                }.onPreviewKeyEvent {
                    if (tv && it.key == Key.DirectionUp && switchProfile != null) {
                        if (it.type == KeyEventType.KeyDown) switchProfile.requestFocus()
                        true
                    } else if (!tv || imeVisible || it.key !in listOf(Key.DirectionCenter, Key.Enter)) false
                    else {
                        if (it.type == KeyEventType.KeyDown) keyboard?.show()
                        true
                    }
                }, label = { Text("Buscar o pegar enlace a una ficha") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = !tv))
                TextButton(onClick = { showFilters = true },
                    modifier = if (tv) Modifier.tvRemoteFocus() else Modifier) {
                    Text(if (filters.isEmpty()) "Filtros" else "Filtros (${filters.size})")
                }
                if (linkedSlug != null) TextButton(onClick = {
                    selected = catalog.find { it.slug == linkedSlug }
                        ?: Dorama(linkedSlug, linkedSlug.replace('-', ' ').replaceFirstChar { it.uppercase() })
                }) { Text("Abrir ficha") }
            }
            if (tab == LibraryTab.Downloads) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DoramaDownloadsList(downloads) { entry ->
                        scope.launch { repository.recordDoramaOpened(entry.episode, entry.title) }
                        offline = entry
                    }
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == LibraryTab.Recent) {
                    if (recentError) item { TextButton(onClick = { recentRetry++ }) { Text("Reintentar últimos episodios") } }
                    else if (recent == null) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    else if (recent.orEmpty().isEmpty()) item { Text("No hay episodios recientes.", Modifier.padding(16.dp)) }
                    items(recent.orEmpty(), key = { it.episode.url }) { publication ->
                        val episode = publication.episode
                        val availability = episodeAvailability(episode)
                        ListItem(
                            headlineContent = { Text(publication.title) },
                            supportingContent = { Text("T${episode.season} · E${episode.number} · ${availability.label()}") },
                            leadingContent = if (publication.poster.isNotEmpty()) ({
                                AsyncImage(publication.poster, null, Modifier.size(58.dp, 44.dp), contentScale = ContentScale.Crop)
                            }) else null,
                            modifier = Modifier.clickable(enabled = availability.canPlay()) { play(episode, publication.title) }
                        )
                    }
                } else if (tab == LibraryTab.History) {
                    if (history.isEmpty()) item { Text("Aún no has abierto episodios.", Modifier.padding(16.dp)) }
                    items(history, key = { it.itemKey }) { row ->
                        val episode = DoramasCatalog.episode(row.itemKey) ?: return@items
                        val title = row.json()?.optString("title").orEmpty().ifBlank { episode.slug }
                        val position = progress[row.itemKey]?.json()?.optLong("positionMs") ?: 0
                        val availability = episodeAvailability(episode)
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Column {
                                    Text("T${episode.season} · E${episode.number} · ${clock(position)}")
                                    Text(availability.label())
                                }
                            },
                            modifier = Modifier.clickable(enabled = availability.canPlay()) { play(episode, title) }
                        )
                    }
                } else {
                    val matching = searchResult?.takeIf { it.first == searchKey }?.second
                    val displayed = when {
                        tab == LibraryTab.Favorites -> favorites.values.sortedBy { it.title }
                        remoteSearch -> matching ?: emptyList()
                        linkedSlug != null -> emptyList()
                        else -> catalog
                    }
                    if (remoteSearch && searchError) {
                        item { TextButton(onClick = { searchRetry++ }) { Text("Reintentar búsqueda") } }
                    } else if (remoteSearch && matching == null) {
                        item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    } else if (displayed.isEmpty() && !loading && error == null && linkedSlug == null) {
                        item {
                            Text(if (tab == LibraryTab.Favorites) "Tu lista está vacía."
                                else if (filters.isNotEmpty()) "No hay resultados con estos filtros."
                                else "No hay resultados.", Modifier.padding(16.dp))
                            if (tab == LibraryTab.Catalog && filters.isNotEmpty()) {
                                TextButton(onClick = { filters = emptyMap() },
                                    modifier = if (tv) Modifier.tvRemoteFocus() else Modifier) { Text("Quitar filtros") }
                            }
                        }
                    }
                    items(displayed, key = { it.slug }) { show ->
                        val caughtUp by produceState(false, show.slug, hideCaughtUp, watched) {
                            value = if (!hideCaughtUp || show.slug !in watchedSlugs) false else try {
                                val published = DoramasCatalog.episodes(show)
                                published.isNotEmpty() && published.all { episode ->
                                    episode.url in watched || available(episode).upcoming
                                }
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                false
                            }
                        }
                        if (hideCaughtUp && caughtUp) return@items
                        ListItem(
                            headlineContent = { Text(show.title) },
                            leadingContent = if (show.poster.isNotBlank()) ({
                                AsyncImage(show.poster, null, Modifier.size(44.dp, 62.dp), contentScale = ContentScale.Crop)
                            }) else null,
                            modifier = Modifier.clickable { selected = show }
                        )
                    }
                    if (tab == LibraryTab.Catalog && searchTerm.isEmpty()) item {
                        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        } else if (hasMore) TextButton(onClick = ::nextPage) {
                            Text(if (error == null) "Cargar más" else "Reintentar catálogo")
                        }
                        error?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
                    }
                }
            }
            NavigationBar {
                LibraryTab.entries.forEach { option ->
                    val icon = when (option) {
                        LibraryTab.Recent -> "◉"
                        LibraryTab.Catalog -> "▦"
                        LibraryTab.Favorites -> "★"
                        LibraryTab.History -> "◷"
                        LibraryTab.Downloads -> "↓"
                    }
                    val focus = if (tv) Modifier.tvRemoteFocus() else Modifier
                    NavigationBarItem(selected = tab == option, onClick = { tab = option },
                        modifier = if (tv && option == tab) focus.focusRequester(navigationFocus) else focus,
                        icon = { Text(icon) }, label = { Text(option.label) })
                }
            }
            LaunchedEffect(Unit) { if (tv) navigationFocus.requestFocus() }
        }
    }
}

private fun ProfileEntry.json(): JSONObject? = runCatching { JSONObject(value) }.getOrNull()

@Composable
private fun episodeAvailability(episode: DoramaEpisode): DoramaAvailability? {
    val result by produceState<DoramaAvailability?>(null, episode) {
        value = available(episode)
    }
    return result
}
private suspend fun available(episode: DoramaEpisode): DoramaAvailability = try {
    DoramasCatalog.availability(episode)
} catch (failure: Exception) {
    if (failure is CancellationException) throw failure
    DoramaAvailability()
}

private fun DoramaAvailability?.canPlay() =
    this != null && !upcoming && (!checked || audio.isNotEmpty())

private fun DoramaAvailability?.label(): String = when {
    this == null -> "Comprobando disponibilidad…"
    upcoming -> airDateMs?.let {
        "Próximamente · ${DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-ES")).format(Date(it))}"
    }
        ?: "Próximamente"
    audio.isNotEmpty() -> audio.joinToString("  |  ")
    checked -> "Sin fuentes publicadas"
    else -> "Disponibilidad sin confirmar"
}

private fun clock(ms: Long): String {
    val seconds = ms / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
