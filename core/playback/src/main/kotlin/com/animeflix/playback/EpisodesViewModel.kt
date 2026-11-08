package com.animeflix.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
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
    fun useInventory(inventory: EpisodeInventory) {
        job?.cancel()
        mutable.value = EpisodesState(inventory.mediaUrl, inventory.episodes)
    }
    fun load(url: String, force: Boolean = false) {
        val base = url.trimEnd('/')
        if (!force && mutable.value.mediaUrl == base) return
        job?.cancel()
        mutable.value = EpisodesState(mediaUrl = base, loading = base.isNotBlank())
        if (base.isBlank()) return
        job = viewModelScope.launch {
            try { mutable.value = EpisodesState(base, AnimeAv1Episodes().episodes(base)) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = EpisodesState(mediaUrl = base, error = true)
            }
        }
    }
}
