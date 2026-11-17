package com.animeflix.mobile

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AniSkipTimes
import com.animeflix.data.AiringSchedule
import com.animeflix.data.scheduleDay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only live metadata acceptance, run explicitly; normal CI uses deterministic fixtures. */
@RunWith(AndroidJUnit4::class)
class PublicMetadataTest {
    @Test fun blackCloverLiveMetadataAndCachedOfflineRead(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val preferences = context.getSharedPreferences("aniskip_times_v1", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("https://animeav1.com/media/black-clover/1",
            """{"at":${System.currentTimeMillis()},"ttl":604800000,"response":"{\"found\":false}"}""").commit()
        val client = AniSkipTimes(context)
        val online = client.load("https://animeav1.com/media/black-clover/1", 1_434_059, false)
        assertEquals(setOf("op", "ed"), online.map { it.type }.toSet())
        assertEquals(180_683L, online.single { it.type == "op" }.startMs)
        assertEquals(online, client.load("https://animeav1.com/media/black-clover/1", 1_434_059, true))
        val recap = client.load("https://animeav1.com/media/black-clover/1", 1_430_037, true).single()
        assertEquals("recap", recap.type)
        assertEquals(103_000L, recap.endMs)
        assertTrue(client.load("https://animeav1.com/media/black-clover/1", 1_000_000, true).isEmpty())
    }

    @Test fun publicScheduleProvidesUniqueSeriesAndRealWeeklyDates(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("airing_schedule_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val feed = AiringSchedule.create(context)
        feed.refresh(force = true)
        assertNull(feed.state.value.error)
        val rows = feed.state.value.items
        assertTrue(rows.isNotEmpty())
        assertEquals(rows.size, rows.map { it.anime.id }.distinct().size)
        assertTrue(rows.all { it.postedAt > 0 && scheduleDay(it.postedAt) in 1..7 })
    }
}
