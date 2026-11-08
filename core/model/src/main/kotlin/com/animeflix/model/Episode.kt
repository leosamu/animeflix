package com.animeflix.model

data class Episode(val number: String, val pageUrl: String) {
    val title: String get() = "Episodio $number"
}

data class PlaybackServer(val name: String, val embedUrl: String)
