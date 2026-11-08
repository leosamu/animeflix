package com.animeflix.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EpisodesState(val mediaUrl: String = "", val items: List<Episode> = emptyList(), val loading: Boolean = false, val error: Boolean = false)

class EpisodesViewModel : ViewModel() {
    private val mutable = MutableStateFlow(EpisodesState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun load(url: String, force: Boolean = false) {
        if (!force && mutable.value.mediaUrl == url) return
        job?.cancel()
        mutable.value = EpisodesState(mediaUrl = url, loading = url.isNotBlank())
        if (url.isBlank()) return
        job = viewModelScope.launch {
            try { mutable.value = EpisodesState(url, AnimeAv1Episodes().episodes(url)) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = EpisodesState(mediaUrl = url, error = true)
            }
        }
    }
}
