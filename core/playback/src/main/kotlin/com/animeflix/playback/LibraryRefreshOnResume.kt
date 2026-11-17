package com.animeflix.playback

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun CatalogRefreshOnResume(enabled: Boolean, refresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRefresh by rememberUpdatedState(refresh)
    LaunchedEffect(lifecycle, enabled) {
        if(enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) { currentRefresh(); delay(15 * 60 * 1000L) }
        }
    }
    DisposableEffect(lifecycle, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_RESUME) currentRefresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
fun rememberScheduleToday(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var today by remember { mutableIntStateOf(com.animeflix.data.scheduleDay()) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while(true) { today = com.animeflix.data.scheduleDay(); delay(60_000) }
    } }
    return today
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
