package com.animeflix.model

import kotlinx.coroutines.flow.Flow

data class Anime(val id: String, val title: String, val synopsis: String, val favorite: Boolean = false,
    val posterUrl: String = "", val sourceUrl: String = "")

interface AnimeRepository {
    fun observeCatalog(): Flow<List<Anime>>
    suspend fun seedDemo()
    suspend fun setFavorite(id: String, favorite: Boolean)
}

/** Future authenticated remote source; no network client or credentials yet. */
interface RemoteAnimeSource { suspend fun fetchCatalog(): List<Anime> }
