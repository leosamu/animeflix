package com.animeflix.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.WatchEntry
import com.animeflix.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WatchHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LocalAnimeRepository.create(app)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val entries = repository.observeWatchHistory().catch { failure ->
        if (failure is CancellationException) throw failure
        mutableError.value = "No se pudo leer el historial local."
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun opened(url: String) = save { repository.recordOpened(url) }
    fun watched(entry: WatchEntry, watched: Boolean) = save { repository.markWatched(entry.episodeUrl, watched) }
    fun mark(url: String, watched: Boolean) = save { repository.markWatched(url, watched) }
    fun previous(episodes: List<Episode>, target: Episode) = save { repository.markPrevious(episodes, target) }
    fun progress(url: String, position: Long, duration: Long) = save { repository.saveProgress(url, position, duration) }
    private fun save(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action(); mutableError.value = null }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableError.value = "No se pudo guardar el historial. Vuelve a intentarlo."
            }
        }
    }
}
