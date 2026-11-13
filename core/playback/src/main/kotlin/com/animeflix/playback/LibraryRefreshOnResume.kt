package com.animeflix.playback

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CatalogRefreshOnResume(enabled: Boolean, refresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRefresh by rememberUpdatedState(refresh)
    LaunchedEffect(enabled) { if (enabled) currentRefresh() }
    DisposableEffect(lifecycle, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_RESUME) currentRefresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

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
