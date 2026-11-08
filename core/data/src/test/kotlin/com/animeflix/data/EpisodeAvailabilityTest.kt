package com.animeflix.data

import com.animeflix.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EpisodeAvailabilityTest {
    private val media = "https://animeav1.com/media/example"
    private fun episode(n: String) = Episode(n, "$media/$n")
    @Test fun countsActualPublishedEpisodesAndExplicitWatchedFlags() {
        val inventory = EpisodeInventory(media, listOf(episode("1"), episode("3"), episode("3"), episode("3.5")), null, 1)
        val history = listOf(WatchEntry("$media/1", media, "1", watched = true),
            WatchEntry("$media/3", media, "3", openedAt = 10, positionMs = 1000),
            WatchEntry("https://animeav1.com/media/other/3.5", "https://animeav1.com/media/other", "3.5", watched = true))
        val status = episodeAvailability(inventory, history)
        assertEquals(3, status.total)
        assertEquals(2, status.pending)
        assertEquals("3.5", status.latestNumber)
        assertEquals("2 pendientes", status.badge)
    }
    @Test fun distinguishesCaughtUpEmptyAndNewlyPublished() {
        val first = episode("1")
        val history = listOf(WatchEntry(first.pageUrl, media, "1", watched = true))
        val inventory = EpisodeInventory(media, listOf(first), null, 1)
        assertEquals("Al día", episodeAvailability(inventory, history).badge)
        assertEquals("1 pendiente", episodeAvailability(inventory.copy(episodes = listOf(first, episode("2"))), history).badge)
        assertEquals("Sin episodios", episodeAvailability(inventory.copy(episodes = emptyList()), history).badge)
        assertTrue(episodeAvailability(inventory, history).latestLabel.contains("Fecha no disponible"))
    }
    @Test fun readsOnlyTheRequestedEpisodesPublicationDateAndPreservesOffsets() {
        val date = "2026-03-26T16:23:54.951562+00:00"
        val html = """media:{publishedAt:"2025-01-01T00:00:00Z",updatedAt:"2026-08-25T00:00:00Z"},episode:{id:1,number:12,variants:{SUB:1},publishedAt:"$date"},embeds:{}"""
        assertEquals(Instant.parse("2026-03-26T16:23:54.951Z").toEpochMilli(), AnimeAv1Episodes.parsePublishedAt(html, "12"))
        assertNull(AnimeAv1Episodes.parsePublishedAt(html, "11"))
        assertNull(AnimeAv1Episodes.parsePublishedAt(html.replace("publishedAt:\"$date\"", "publishedAt:null"), "12"))
        assertNull(AnimeAv1Episodes.parsePublishedAt(html.replace(date, "2026-02-30T00:00:00Z"), "12"))
        assertEquals(Instant.parse("2026-03-26T16:23:54.951Z").toEpochMilli(), AnimeAv1Episodes.parsePublishedAt(html.replace(date, "2026-03-26T18:23:54.951+02:00"), "12"))
    }
}
