package com.animeflix.data

import com.animeflix.model.Episode
import com.animeflix.model.WatchEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keep the full ledger for watched marks and progress; collapse only the visible history. */
fun latestHistoryPerSeries(entries: List<WatchEntry>): List<WatchEntry> = entries.filter { it.openedAt > 0 }
    .sortedWith(compareByDescending<WatchEntry> { it.openedAt }.thenBy { it.episodeUrl })
    .distinctBy { it.mediaUrl.trimEnd('/') }

fun playbackReachedEnd(position: Long, duration: Long): Boolean =
    duration in 60_000..86_400_000 && position in (duration - 1_500)..(duration + 1_500)

/** Use the published inventory, including specials; never guess a URL or skip a missing offline episode. */
fun nextPublishedEpisode(current: String, episodes: List<Episode>): String? {
    if (!AnimeAv1Episodes.isEpisodeUrl(current)) return null
    val media = current.substringBeforeLast('/')
    val number = current.substringAfterLast('/').toBigDecimal()
    return episodes.filter { AnimeAv1Episodes.isEpisodeUrl(it.pageUrl) && it.pageUrl.substringBeforeLast('/') == media &&
        it.number == it.pageUrl.substringAfterLast('/') && it.number.toBigDecimal() > number }
        .minByOrNull { it.number.toBigDecimal() }?.pageUrl
}

fun nextOfflineEpisode(current: String, published: List<Episode>, downloaded: List<String>): String? {
    val next = nextPublishedEpisode(current, published)
    if (next != null) return next.takeIf { it in downloaded }
    if (!AnimeAv1Episodes.isEpisodeUrl(current)) return null
    val number = current.substringAfterLast('/').toBigDecimal()
    if (number.stripTrailingZeros().scale() > 0) return null
    return downloaded.firstOrNull { AnimeAv1Episodes.isEpisodeUrl(it) && it.substringBeforeLast('/') == current.substringBeforeLast('/') &&
        it.substringAfterLast('/').toBigDecimal().compareTo(number + java.math.BigDecimal.ONE) == 0 }
}

/** The player can emit ENDED repeatedly. Persist the watched mark before attempting to advance once. */
class EpisodeCompletionHandler(
    private val saveProgress: suspend (String, Long, Long) -> Unit,
    private val markWatched: suspend (String) -> Unit,
    private val next: suspend (String) -> String?
) {
    private val mutex = Mutex()
    private val finished = mutableSetOf<String>()
    suspend fun started(url: String) = mutex.withLock { finished.remove(url) }
    suspend fun complete(url: String, position: Long, duration: Long): String? = mutex.withLock {
        if (!AnimeAv1Episodes.isEpisodeUrl(url) || !playbackReachedEnd(position, duration) || url in finished) return@withLock null
        saveProgress(url, position.coerceAtMost(duration), duration)
        markWatched(url)
        finished.add(url)
        next(url)
    }
}
