package com.animeflix.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.data.AnimeAv1Catalog
import com.animeflix.model.Anime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class CatalogState(val items: List<Anime> = emptyList(), val loading: Boolean = true, val error: String? = null,
    val refreshing: Boolean = false, val canLoadMore: Boolean = false, val airing: List<com.animeflix.data.AiringAnime> = emptyList(), val airingLoading: Boolean = false, val airingError: String? = null, val watched: Set<String> = emptySet())
private data class SyncState(val refreshing: Boolean = false, val more: Boolean = false)

class CatalogViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val repository = ProfileAnimeRepository.create(app)
    private val refresh = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)
    private val syncState = MutableStateFlow(SyncState())
    private val remote = AnimeAv1Catalog()
    private val schedule = com.animeflix.data.AiringSchedule.create(app)
    private var scheduleRequest: Job? = null
    private var request: Job? = null
    private var page = 0
    init { if(saved.get<Int>("navigationRevision") != 1) { saved["section"] = "airing"; saved["query"] = ""; saved["selected"] = null; saved["navigationRevision"] = 1 } }
    val query = saved.getStateFlow("query", "")
    val section = saved.getStateFlow("section", "airing")
    val selectedId = saved.getStateFlow<String?>("selected", null)
    val hideCaughtUp = repository.observeHideCaughtUp().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setHideCaughtUp(value: Boolean) {
        viewModelScope.launch {
            try { repository.setHideCaughtUp(value); actionError.value = null }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                actionError.value = "No se pudo guardar la preferencia. Inténtalo de nuevo."
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest {
        flow {
            emit(CatalogState())
            if (false) repository.seedDemo()
            emitAll(repository.observeCatalog().map { items ->
                CatalogState(items = if (false) items else items.filter { it.sourceUrl.isNotBlank() }, loading = false)
            })
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(CatalogState(loading = false, error = "No se pudo cargar el catálogo local."))
        }
    }.combine(schedule.state) { catalog, feed ->
        val combined = catalog.items + feed.items.map { it.anime }.filter { anime -> catalog.items.none { it.id == anime.id } }
        catalog.copy(items = combined, airing = feed.items, airingLoading = !false && feed.loading, airingError = feed.error)
    }.combine(repository.observeWatchHistory()) { catalog, history ->
        catalog.copy(watched = history.filter { it.watched }.map { it.episodeUrl }.toSet())
    }.combine(section) { catalog, section ->
        catalog.copy(items = catalog.items.sortedBy { it.title.lowercase() }, error = if(section == "airing") catalog.airingError else catalog.error)
    }.combine(actionError) { catalog, error -> catalog.copy(error = error ?: catalog.error) }
        .combine(syncState) { catalog, sync -> catalog.copy(refreshing = if(section.value == "airing") catalog.airingLoading else sync.refreshing, canLoadMore = sync.more) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogState())

    init { if (!false) { refreshSchedule(); if(section.value == "catalog") load(reset = true) } }

    fun search(value: String) {
        saved["query"] = value
        if (!false && section.value == "catalog") load(reset = true, debounce = true)
    }
    fun navigate(value: String) { saved["section"] = value; saved["selected"] = null; if(value == "airing") refreshSchedule(); if(value == "catalog" && page == 0 && !false) load(reset = true) }
    fun open(id: String?) { saved["selected"] = id }
    fun retry() {
        actionError.value = null
        refresh.value++
        if(section.value == "airing") { refreshSchedule(force = true); return }
        if (!false) load(reset = true)
    }
    fun refreshSchedule(force: Boolean = false) {
        if (false || scheduleRequest?.isActive == true) return
        scheduleRequest = viewModelScope.launch { schedule.refresh(force) }
    }
    fun loadMore() { if (!syncState.value.refreshing && syncState.value.more) load(reset = false) }

    private fun load(reset: Boolean, debounce: Boolean = false) {
        request?.cancel()
        request = viewModelScope.launch {
            syncState.value = SyncState(refreshing = true)
            if (debounce) delay(600)
            try {
                val next = if (reset) 1 else page + 1
                val result = remote.fetch(next, query.value)
                repository.cacheCatalog(result.items)
                page = next
                actionError.value = null
                syncState.value = SyncState(more = result.hasMore)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                actionError.value = "No se pudo actualizar AnimeAV1. Puedes consultar los títulos guardados y reintentar."
                syncState.value = SyncState(more = page > 0)
            }
        }
    }
    fun toggleFavorite(anime: Anime) {
        viewModelScope.launch {
            try {
                repository.setFavorite(anime.id, !anime.favorite)
                actionError.value = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                actionError.value = "No se pudo guardar el favorito. Inténtalo de nuevo."
            }
        }
    }
}
