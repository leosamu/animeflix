package com.animeflix.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EpisodeInventory(val mediaUrl: String, val episodes: List<Episode>, val latestPublishedAt: Long?, val checkedAt: Long)
data class EpisodeAvailability(val total: Int, val pending: Int, val latestNumber: String?, val publishedAt: Long?, val checkedAt: Long) {
    val badge: String get() = when { total == 0 -> "Sin episodios"; pending == 0 -> "Al día"; pending == 1 -> "1 pendiente"; else -> "$pending pendientes" }
    val latestLabel: String get() = latestNumber?.let { number ->
        "Último: episodio $number · " + (publishedAt?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "Fecha no disponible")
    } ?: "Todavía sin episodios publicados"
}

fun episodeAvailability(inventory: EpisodeInventory, history: List<WatchEntry>): EpisodeAvailability {
    val episodes = inventory.episodes.distinctBy { it.pageUrl }
    val seen = history.filter { it.watched && it.mediaUrl.trimEnd('/') == inventory.mediaUrl.trimEnd('/') }.map { it.episodeUrl }.toSet()
    return EpisodeAvailability(episodes.size, episodes.count { it.pageUrl !in seen },
        episodes.maxByOrNull { it.number.toBigDecimal() }?.number, inventory.latestPublishedAt, inventory.checkedAt)
}
