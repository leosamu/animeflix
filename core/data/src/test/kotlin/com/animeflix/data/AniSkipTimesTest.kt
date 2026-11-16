package com.animeflix.data

import org.junit.Assert.*
import org.junit.Test

class AniSkipTimesTest {
    private fun response(vararg rows: String) = """{"found":true,"results":[${rows.joinToString()}]}"""
    private fun row(type: String, start: Double, end: Double, duration: Double = 120.0) =
        """{"skipType":"$type","interval":{"startTime":$start,"endTime":$end},"episodeLength":$duration}"""

    @Test fun identifiesExactSeriesWithoutConfusingGenreIdsOrAnotherSeason() {
        val html = """genres:[{malId:17}],slug:"black-clover",malId:34572,seasons:null"""
        assertEquals(34572L, parseAnimeMalId(html, "black-clover"))
        assertNull(parseAnimeMalId(html, "black-clover-2nd-season"))
        assertNull(parseAnimeMalId(html, "../black-clover"))
    }
    @Test fun onlyShowsCompatibleOpeningAndEndingIntervals() {
        val intervals = parseSkipTimes(response(row("op", 10.0, 30.0), row("ed", 80.0, 100.0), row("mixed-ed", 40.0, 60.0)), 120_000)
        assertEquals(listOf("op", "ed"), intervals.map { it.type })
        assertTrue(intervals.first().active(10_000, 120_000))
        assertFalse(intervals.first().active(30_000, 120_000))
        assertTrue(parseSkipTimes(response(row("op", 10.0, 30.0)), 125_000).isEmpty())
    }
    @Test fun endingPreservesPostCreditsAndNeverTargetsEndOfFile() {
        val postCredits = parseSkipTimes(response(row("ed", 80.0, 100.0)), 120_000).single()
        assertEquals(100_000L, postCredits.target(120_000))
        val finalEnding = parseSkipTimes(response(row("ed", 80.0, 120.0)), 120_000).single()
        assertEquals(117_500L, finalEnding.target(120_000))
        assertFalse(finalEnding.active(118_000, 120_000))
    }
    @Test fun choosesClosestMatchingVersionAndRejectsUnsafeIntervals() {
        assertEquals(11_000L, parseSkipTimes(response(row("op", 10.0, 30.0, 121.5), row("op", 11.0, 31.0, 120.1)), 120_000).single().startMs)
        assertTrue(parseSkipTimes(response(row("op", -1.0, 20.0), row("ed", 100.0, 90.0), row("ed", 110.0, 130.0)), 120_000).isEmpty())
    }
    @Test fun absentOrBrokenMetadataLeavesPlayerUnchanged() {
        assertTrue(parseSkipTimes("{\"found\":false}", 120_000).isEmpty())
        assertTrue(parseSkipTimes("not json", 120_000).isEmpty())
        assertTrue(parseSkipTimes(response(row("op", 10.0, 30.0)), -1).isEmpty())
    }
}
