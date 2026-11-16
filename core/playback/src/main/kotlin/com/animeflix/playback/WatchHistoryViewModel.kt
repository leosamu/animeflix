package com.animeflix.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.data.EpisodeCompletionHandler
import com.animeflix.data.nextPublishedEpisode
import com.animeflix.data.nextOfflineEpisode
import com.animeflix.model.WatchEntry
import com.animeflix.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContinuationTarget(val url: String, val offlineId: Long = -1)

class WatchHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ProfileAnimeRepository.create(app)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableCompleting = MutableStateFlow(false)
    val completing = mutableCompleting.asStateFlow()
    private val downloads = EpisodeDownloads(app)
    private var offlineCompletion = false
    private val completion = EpisodeCompletionHandler(repository::saveProgress, { repository.markWatched(it, true) }) { url ->
        val media = url.substringBeforeLast('/')
        val inventory = repository.observeInventories().first().find { it.mediaUrl == media }?.episodes.orEmpty()
        if (offlineCompletion) {
            val available = withContext(Dispatchers.IO) { downloads.list().filter { it.complete } }
            nextOfflineEpisode(url, inventory, available.map { it.episodeUrl })
        } else {
            nextPublishedEpisode(url, inventory) ?: try { nextPublishedEpisode(url, AnimeAv1Episodes().episodes(media)) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableError.value = "El capítulo está marcado como visto, pero no se pudo comprobar el siguiente."
                null
            }
        }
    }
    val entries = repository.observeWatchHistory().catch { failure ->
        if (failure is CancellationException) throw failure
        mutableError.value = "No se pudo leer el historial local."
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun opened(url: String) = save { completion.started(url); repository.recordOpened(url) }
    fun watched(entry: WatchEntry, watched: Boolean) = save { repository.markWatched(entry.episodeUrl, watched) }
    fun mark(url: String, watched: Boolean) = save { repository.markWatched(url, watched) }
    fun previous(episodes: List<Episode>, target: Episode) = save { repository.markPrevious(episodes, target) }
    fun progress(url: String, position: Long, duration: Long) = save { repository.saveProgress(url, position, duration) }
    fun completed(url: String, position: Long, duration: Long, offline: Boolean, onNext: (ContinuationTarget) -> Unit) {
        if (mutableCompleting.value) return
        mutableCompleting.value = true
        viewModelScope.launch {
            try {
                mutableError.value = null
                offlineCompletion = offline
                completion.complete(url, position, duration)?.let { next ->
                    if (offline) {
                        withContext(Dispatchers.IO) { downloads.list().find { it.complete && it.episodeUrl == next } }
                            ?.let { onNext(ContinuationTarget(next, it.id)) }
                    } else onNext(ContinuationTarget(next))
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableError.value = "No se pudo guardar el final del capítulo. Puedes marcarlo como visto e iniciar el siguiente manualmente."
            } finally { mutableCompleting.value = false }
        }
    }
    suspend fun savedPosition(url: String): Long = repository.observeWatchHistory().first().firstOrNull { it.episodeUrl == url }?.let {
        if (com.animeflix.data.playbackReachedEnd(it.positionMs, it.durationMs)) 0 else it.positionMs
    } ?: 0
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
