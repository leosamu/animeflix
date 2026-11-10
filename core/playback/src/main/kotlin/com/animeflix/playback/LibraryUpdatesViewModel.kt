package com.animeflix.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LibraryUpdatesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProfileAnimeRepository.create(app)
    private val remote = AnimeAv1Episodes()
    private val requests = mutableSetOf<String>()
    private val network = Semaphore(3)
    private var active = 0
    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing = mutableRefreshing.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val inventories = repository.observeInventories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val statuses = combine(inventories, repository.observeWatchHistory()) { entries, history ->
        entries.associate { it.mediaUrl to episodeAvailability(it, history) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun refreshFavorites(force: Boolean = false) {
        if(force) mutableError.value = null
        viewModelScope.launch {
            try { repository.observeCatalog().first().filter { it.favorite }.forEach { check(it.sourceUrl, force) } }
            catch(failure: Exception) { if(failure is CancellationException) throw failure; mutableError.value = "No se pudo comprobar Mi lista." }
        }
    }
    fun check(url: String, force: Boolean = false) {
        val base = url.trimEnd('/')
        if(!AnimeAv1Episodes.isMediaUrl(base) || base in requests) return
        requests.add(base); active++; mutableRefreshing.value = true
        viewModelScope.launch {
            try {
                val previous = repository.observeInventories().first().find { it.mediaUrl == base }
                if(!force && previous != null && System.currentTimeMillis() - previous.checkedAt in 0L until 15 * 60 * 1000L) return@launch
                network.withPermit { repository.cacheInventory(remote.inventory(base, if(force) null else previous)) }
            }
            catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                mutableError.value = "No se pudieron comprobar algunos episodios. Se conservan los últimos datos guardados."
            } finally { requests.remove(base); active--; mutableRefreshing.value = active > 0 }
        }
    }
}
