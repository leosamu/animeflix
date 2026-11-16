package com.animeflix.data

import com.animeflix.model.Anime
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal data class RecentPublication(val anime: Anime, val number: String, val publishedAt: Long)

/** Read serialised public data without executing JavaScript; braces inside quoted titles are ignored. */
internal fun publicObjects(html: String, marker: String): List<String> {
    val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return emptyList()
    val objects = mutableListOf<String>()
    var quoted = false; var escaped = false; var depth = 0; var begin = -1
    for (i in start until html.length) {
        val c = html[i]
        if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false; continue }
        if (c == '"') { quoted = true; continue }
        if (c == ']' && depth == 0) break
        if (c == '{') { if (depth == 0) begin = i; depth++ }
        if (c == '}') { depth--; if (depth == 0 && begin >= 0) { objects += html.substring(begin, i + 1); if (objects.size >= 500) break } }
    }
    return objects
}

private fun field(block: String, name: String): String? = Regex("(?:^|[,{])${Regex.escape(name)}:(\"(?:[^\"\\\\]|\\\\.)*\")")
    .find(block)?.groupValues?.get(1)?.let { runCatching { JSONObject("{\"v\":$it}").getString("v") }.getOrNull() }

internal fun publicationTime(raw: String): Long? {
    val match = Regex("(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)").matchEntire(raw) ?: return null
    val zone = match.groupValues[4].let { if (it == "Z") "+0000" else it.replace(":", "").padEnd(5, '0') }
    val value = "${match.groupValues[1]}T${match.groupValues[2]}.${match.groupValues[3].take(3).padEnd(3, '0')}$zone"
    val position = ParsePosition(0)
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply { isLenient = false }.parse(value, position)?.time
        ?.takeIf { it > 0 && position.index == value.length }
}

internal fun episodeReleaseTime(html: String, expectedNumber: String): Long? {
    val block = html.substringAfter("episode:{", "").substringBefore("},embeds:")
    val number = Regex("(?:^|,)number:([0-9]+(?:\\.[0-9]+)?)(?=[,}])").find(block)?.groupValues?.get(1) ?: return null
    if (number.toBigDecimal().compareTo(expectedNumber.toBigDecimal()) != 0) return null
    field(block, "publishedAt")?.let { return publicationTime(it) }
    // Older/public episode records expose upload time and explicitly leave publication time null.
    if (!Regex("(?:^|,)publishedAt:null(?:,|$)").containsMatchIn(block)) return null
    return field(block, "createdAt")?.let(::publicationTime)
}

internal fun parsePublications(html: String, schedule: Boolean = false): List<RecentPublication> =
    publicObjects(html, if (schedule) "data:{media:[" else "latestEpisodes:[").mapNotNull { block ->
        val slug = field(block, "slug")?.takeIf { Regex("[a-zA-Z0-9_-]+").matches(it) } ?: return@mapNotNull null
        val title = field(block, "title") ?: return@mapNotNull null
        val id = Regex(if (schedule) "^\\{id:([0-9]+)" else "media:\\{id:([0-9]+)").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
        val episode = if (schedule) Regex("latestEpisode:\\{([^}]+)\\}").find(block)?.groupValues?.get(1) ?: return@mapNotNull null else block
        val number = Regex("(?:^|[,{}])number:([0-9]+(?:\\.[0-9]+)?)(?=[,}])").find(episode)?.groupValues?.get(1) ?: return@mapNotNull null
        val date = field(episode, if (schedule) "createdAt" else "publishedAt")?.let(::publicationTime) ?: return@mapNotNull null
        RecentPublication(Anime("av1:$slug", title, field(block, "synopsis") ?: "Sin sinopsis disponible.",
            posterUrl = "https://cdn.animeav1.com/covers/$id.jpg", sourceUrl = "https://animeav1.com/media/$slug"), number, date)
    }

internal fun recentPublications(current: List<RecentPublication>, cached: List<RecentPublication>): List<RecentPublication> =
    (current + cached).sortedWith(compareByDescending<RecentPublication> { it.publishedAt }.thenByDescending { it.number.toBigDecimal() })
        .distinctBy { it.anime.id }.take(24)
