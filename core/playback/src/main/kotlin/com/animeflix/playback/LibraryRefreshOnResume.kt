package com.animeflix.playback

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun LibraryRefreshOnResume(model: LibraryUpdatesViewModel, mediaUrl: String?, favorites: Boolean, enabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun refresh() {
        if(enabled) {
            if(favorites) model.refreshFavorites()
            mediaUrl?.let { model.check(it) }
        }
    }
    LaunchedEffect(mediaUrl, favorites, enabled) { refresh() }
    DisposableEffect(lifecycle, mediaUrl, favorites, enabled) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
