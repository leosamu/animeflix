package com.animeflix.model

/** An opened episode is not evidence that playback started or finished. */
data class WatchEntry(val episodeUrl: String, val mediaUrl: String, val number: String,
    val openedAt: Long = 0, val watched: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)

fun playbackTime(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
