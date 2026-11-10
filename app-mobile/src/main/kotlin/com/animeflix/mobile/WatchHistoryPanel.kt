package com.animeflix.mobile

import com.animeflix.playback.PlayerActivity
import com.animeflix.playback.WatchHistoryViewModel

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animeflix.playback.profileViewModel
import com.animeflix.model.Anime

internal fun openEpisode(context: Context, url: String, title: String) {
    context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", url).putExtra("title", title))
}

@Composable
fun WatchHistoryPanel(catalog: List<Anime>, model: WatchHistoryViewModel = profileViewModel()) {
    val entries by model.entries.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HistoryContent(catalog, entries, error,
        onPlay = { openEpisode(context, it.episodeUrl, "Episodio ${it.number}") },
        onWatched = { model.watched(it, !it.watched) })
}
