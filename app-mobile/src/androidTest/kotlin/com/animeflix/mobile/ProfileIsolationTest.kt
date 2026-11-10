package com.animeflix.mobile

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileIsolationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".qa")) }

    @Test fun profilesKeepFavoritesHistoryProgressAndPreferencesSeparate() = runBlocking {
        val accounts = AccountManager.create(context)
        accounts.beginPreview()
        try {
            val boy = accounts.state.value.profiles.first { it.name == "Niño" }
            val girl = accounts.state.value.profiles.first { it.name == "Niña" }
            accounts.select(boy)
            val boys = ProfileAnimeRepository.create(context)
            boys.seedDemo()
            boys.setFavorite("demo-1", true)
            val episode = "https://animeav1.com/media/example/1"
            boys.recordOpened(episode, 100)
            boys.markWatched(episode, true)
            boys.saveProgress(episode, 45_000, 120_000)
            boys.setHideCaughtUp(true)
            accounts.select(girl)
            val girls = ProfileAnimeRepository.create(context)
            girls.seedDemo()
            assertTrue(girls.observeCatalog().first().none { it.favorite })
            assertTrue(girls.observeWatchHistory().first().isEmpty())
            assertFalse(girls.observeHideCaughtUp().first())
            girls.setFavorite("demo-2", true)
            girls.markWatched("https://animeav1.com/media/example/2", true)
            // A delayed player callback remains bound to its original profile after a switch.
            boys.saveProgress(episode, 60_000, 120_000)
            assertEquals(listOf("2"), girls.observeWatchHistory().first().map { it.number })
            accounts.select(boy)
            val returned = ProfileAnimeRepository.create(context)
            assertEquals(listOf("demo-1"), returned.observeCatalog().first().filter { it.favorite }.map { it.id })
            assertTrue(returned.observeHideCaughtUp().first())
            val history = returned.observeWatchHistory().first().single()
            assertTrue(history.watched)
            assertEquals(100L, history.openedAt)
            assertEquals(60_000L, history.positionMs)
            accounts.signOut()
            accounts.beginPreview()
            accounts.select(accounts.state.value.profiles.first())
            assertTrue(ProfileAnimeRepository.create(context).observeWatchHistory().first().isEmpty())
        } finally { accounts.signOut() }
    }

    @Test fun staleAcknowledgementAndRemotePullCannotErasePendingChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java).build()
        try {
            val dao = db.profileEntryDao()
            val initial = ProfileEntry("watched", "episode", "{\"watched\":true}", revision = "first")
            dao.put(initial)
            val newer = initial.copy(value = "{\"watched\":false}", revision = "second")
            dao.put(newer)
            dao.acknowledge("watched", "episode", "first")
            assertEquals(newer, dao.pending().single())
            dao.receive(listOf(initial.copy(dirty = false)))
            assertEquals(newer, dao.get("watched", "episode"))
            dao.acknowledge("watched", "episode", "second")
            assertTrue(dao.pending().isEmpty())
            dao.receive(listOf(initial.copy(dirty = false)))
            assertEquals(initial.copy(dirty = false), dao.get("watched", "episode"))
        } finally { db.close() }
    }
}
