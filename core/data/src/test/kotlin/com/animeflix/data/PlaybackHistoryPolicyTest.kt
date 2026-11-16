package com.animeflix.data

import com.animeflix.model.Episode
import com.animeflix.model.WatchEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackHistoryPolicyTest {
    private val media = "https://animeav1.com/media/example"
    private fun episode(number: String) = Episode(number, "$media/$number")

    @Test fun historySelectsLastOpenedPerSeriesRatherThanHighestEpisodeOrBulkMarkedEntries() {
        val entries = listOf(WatchEntry("$media/12", media, "12", 100, true),
            WatchEntry("$media/2", media, "2", 200, false, 30_000, 120_000),
            WatchEntry("$media/13", media, "13", 0, true),
            WatchEntry("https://animeav1.com/media/other/1", "https://animeav1.com/media/other", "1", 150))
        assertEquals(listOf("2", "1"), latestHistoryPerSeries(entries).map { it.number })
        assertEquals(4, entries.size)
        assertEquals(30_000L, latestHistoryPerSeries(entries).first().positionMs)
    }

    @Test fun publishedNextHandlesSpecialsUnorderedInventoryAndEndOfSeries() {
        val inventory = listOf(episode("3"), episode("2.5"), episode("2"), Episode("4", "https://animeav1.com/media/other/4"))
        assertEquals("$media/2.5", nextPublishedEpisode("$media/2", inventory))
        assertEquals("$media/3", nextPublishedEpisode("$media/2.5", inventory))
        assertNull(nextPublishedEpisode("$media/3", inventory))
        assertNull(nextPublishedEpisode("invalid", inventory))
    }

    @Test fun endRequiresARealisticEpisodeAndActualEndRatherThanCreditsOrNinetyFivePercent() {
        assertFalse(playbackReachedEnd(1_140_000, 1_200_000))
        assertFalse(playbackReachedEnd(10_000, 10_000))
        assertFalse(playbackReachedEnd(1_201_501, 1_200_000))
        assertTrue(playbackReachedEnd(1_200_002, 1_200_000))
        assertTrue(playbackReachedEnd(1_200_000, 1_200_000))
    }

    @Test fun offlineContinuesOnlyToNextDownloadedChapterWithoutSkippingGapsOrSpecials() {
        val inventory = listOf(episode("1"), episode("2"), episode("2.5"), episode("3"))
        assertEquals("$media/2", nextOfflineEpisode("$media/1", inventory, listOf("$media/2", "$media/3")))
        assertNull(nextOfflineEpisode("$media/1", inventory, listOf("$media/3")))
        assertNull(nextOfflineEpisode("$media/2", inventory, listOf("$media/3")))
        assertEquals("$media/2", nextOfflineEpisode("$media/1", emptyList(), listOf("$media/2")))
        assertNull(nextOfflineEpisode("$media/1", emptyList(), listOf("$media/3")))
        assertNull(nextOfflineEpisode("$media/2.5", emptyList(), listOf("$media/3")))
    }

    @Test fun duplicateEndEventsSaveAndMarkBeforeAdvancingOnlyOnce(): Unit = runBlocking {
        val actions = mutableListOf<String>()
        val completion = EpisodeCompletionHandler({ _, position, duration -> assertEquals(duration, position); actions += "progress" }, { actions += "watched" }, {
            actions += "next"; "$media/2"
        })
        val results = (1..3).map { async { completion.complete("$media/1", 120_002, 120_000) } }.awaitAll()
        assertEquals(listOf("progress", "watched", "next"), actions)
        assertEquals(1, results.count { it != null })
        completion.started("$media/1")
        assertEquals("$media/2", completion.complete("$media/1", 120_000, 120_000))
    }

    @Test fun finalEpisodeStillBecomesWatchedWhenThereIsNoNext(): Unit = runBlocking {
        var watched = false
        val completion = EpisodeCompletionHandler({ _, _, _ -> }, { watched = true }, { null })
        assertNull(completion.complete("$media/1", 120_000, 120_000))
        assertTrue(watched)
    }

    @Test fun failedSavePreventsAdvanceAndCanBeRetried(): Unit = runBlocking {
        var fail = true
        var advanced = false
        val completion = EpisodeCompletionHandler({ _, _, _ -> if (fail) error("storage failure") }, {}, { advanced = true; "$media/2" })
        assertTrue(runCatching { completion.complete("$media/1", 120_000, 120_000) }.isFailure)
        assertFalse(advanced)
        fail = false
        assertEquals("$media/2", completion.complete("$media/1", 120_000, 120_000))
    }
}
