package com.animeflix.data

import com.animeflix.model.Anime
import com.animeflix.model.EpisodeAvailability
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class AiringScheduleTest {
    @Test fun tuesdayStartsWithTodayThenLooksBackAcrossWeekBoundary() {
        assertEquals(listOf(2, 1, 7, 6, 5, 4, 3), scheduleDays(2))
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), scheduleDays(7))
        assertEquals(7, scheduleDays(1).distinct().size)
    }
    @Test fun usesLocalWeekdayLikeThePublicScheduleInsteadOfAssumingUtc() {
        val at = checkNotNull(publicationTime("2026-11-17T00:30:00Z"))
        assertEquals(2, scheduleDay(at, TimeZone.getTimeZone("Europe/Madrid")))
        assertEquals(1, scheduleDay(at, TimeZone.getTimeZone("America/Los_Angeles")))
    }
    @Test fun groupsByWeekdayAndKeepsChronologicalTimesInsideEachDay() {
        fun row(name: String, date: String) = AiringAnime(Anime(name, name, ""), "1", checkNotNull(publicationTime(date)))
        val rows = listOf(row("Wednesday", "2026-11-11T12:00:00Z"), row("Monday", "2026-11-16T12:00:00Z"),
            row("Tuesday late", "2026-11-17T18:00:00Z"), row("Sunday", "2026-11-15T12:00:00Z"), row("Tuesday early", "2026-11-17T08:00:00Z"))
        assertEquals(listOf("Tuesday early", "Tuesday late", "Monday", "Sunday", "Wednesday"), orderAiring(rows, 2, TimeZone.getTimeZone("UTC")).map { it.anime.title })
    }
    @Test fun caughtUpDoesNotReappearJustBecauseItsWeekdayArrivesButNewPublishedChapterDoes() {
        val anime = Anime("a", "Serie", "", sourceUrl = "https://animeav1.com/media/example")
        val status = EpisodeAvailability(3, 0, "3", null, 100)
        val row = AiringAnime(anime, "3", 100)
        val watched = setOf("${anime.sourceUrl}/1", "${anime.sourceUrl}/2", "${anime.sourceUrl}/3")
        assertTrue(row.caughtUp(status, watched))
        assertFalse(row.copy(latestNumber = "4").caughtUp(status, watched))
        assertFalse(row.caughtUp(status.copy(pending = 1), watched))
        assertFalse(row.caughtUp(null, watched))
    }
    @Test fun parsesTheSameLatestEpisodeTimestampUsedByTheWebsiteAndDeduplicatesSeries() {
        val row = """{id:1,title:"Serie & amigos",synopsis:"Sinopsis",slug:"example",createdAt:"2020-01-01T00:00:00Z",latestEpisode:{id:2,number:3,createdAt:"2026-11-17T18:00:00Z"}}"""
        val rows = parseAiringSchedule("data:{media:[$row,$row,{id:3,title:\"Sin capítulo\",slug:\"empty\",latestEpisode:null}]}")
        assertEquals(1, rows.size)
        assertEquals("https://animeav1.com/media/example/3", rows.single().latestUrl)
        assertEquals(checkNotNull(publicationTime("2026-11-17T18:00:00Z")), rows.single().postedAt)
    }
}
