package com.animeflix.data

import com.animeflix.model.Anime
import org.junit.Assert.*
import org.junit.Test

class RecentPublicationsTest {
    @Test fun verifiesEpisodeNumberAndUsesUploadTimeOnlyForExplicitlyNullPublicationTime() {
        val html = """episode:{id:60715,number:26,variants:{SUB:1},publishedAt:null,createdAt:"2026-11-11T14:49:17.240612+00:00"},embeds:{}"""
        assertEquals(publicationTime("2026-11-11T14:49:17.240Z"), episodeReleaseTime(html, "26"))
        assertNull(episodeReleaseTime(html, "25"))
        assertNull(episodeReleaseTime(html.replace("publishedAt:null,", ""), "26"))
    }
    @Test fun readsActualPublicationDatesAndQuotedBracesWithoutExecutingScripts() {
        val html = """latestEpisodes:[{commentsCount:9,createdAt:"2026-11-12 17:00:00+00",media:{id:4486,slug:"example",title:"Una {historia} \"nueva\""},number:6,publishedAt:"2026-11-12 17:53:11.821+00"}],other:[]"""
        val item = parsePublications(html).single()
        assertEquals("Una {historia} \"nueva\"", item.anime.title)
        assertEquals("6", item.number)
        assertEquals(publicationTime("2026-11-12T17:53:11.821Z"), item.publishedAt)
    }
    @Test fun scheduleDatesAreOnlyCandidateDatesAndKeepLatestEpisodeNumber() {
        val html = """data:{media:[{id:3137,title:"Yamato",synopsis:"Texto",slug:"yamato",createdAt:"2020-01-01T00:00:00Z",latestEpisode:{id:60715,number:26,createdAt:"2026-11-11T14:49:17.240612+00:00"}}]}"""
        val item = parsePublications(html, schedule = true).single()
        assertEquals("26", item.number)
        assertEquals(publicationTime("2026-11-11T14:49:17.240Z"), item.publishedAt)
    }
    @Test fun newestTwentyFourSeriesAreSortedByPublicationDateAndNeverAlphabeticallyPadded() {
        fun item(n: Int) = RecentPublication(Anime("a$n", "Título $n", ""), "1", 1_000L + n)
        val rows = recentPublications((1..32).map(::item).reversed() + listOf(item(3).copy(number = "2", publishedAt = 2_000)), listOf(item(1)))
        assertEquals(24, rows.size)
        assertEquals("a3", rows.first().anime.id)
        assertEquals("a32", rows[1].anime.id)
        assertEquals(24, rows.map { it.anime.id }.distinct().size)
        assertTrue(rows.zipWithNext().all { it.first.publishedAt >= it.second.publishedAt })
        assertEquals(1, recentPublications(listOf(item(1)), emptyList()).size)
    }
    @Test fun unknownDatesAreNotInventedAndOffsetsAreHandled() {
        assertNull(publicationTime("yesterday"))
        assertEquals(publicationTime("2026-11-12T17:00:00Z"), publicationTime("2026-11-12T18:00:00+01:00"))
        assertTrue(parsePublications("latestEpisodes:[{media:{id:1,slug:\"unknown\",title:\"Unknown\"},number:1}]").isEmpty())
    }
}
