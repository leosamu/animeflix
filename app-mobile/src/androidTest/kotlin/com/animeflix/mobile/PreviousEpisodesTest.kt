package com.animeflix.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.Episode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviousEpisodesTest {
    @Test fun marksOnlyRealPreviousEpisodesAcrossPagesWithoutLosingProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val repository = LocalAnimeRepository.create(context)
        val media = "https://animeav1.com/media/qa-bulk"
        val episodes = (1..55).map { Episode(it.toString(), "$media/$it") } + Episode("2.5", "$media/2.5")
        repository.recordOpened("$media/1", 123)
        repository.saveProgress("$media/1", 30_000, 120_000)
        repository.markWatched("$media/52", false)
        repository.markWatched("$media/53", false)
        repository.markWatched("https://animeav1.com/media/qa-other/1", false)
        repository.markPrevious(episodes, episodes.single { it.number == "52" })
        val entries = repository.observeWatchHistory().first().associateBy { it.episodeUrl }
        assertEquals(52, entries.values.count { it.mediaUrl == media && it.watched })
        assertFalse(entries.getValue("$media/52").watched)
        assertFalse(entries.getValue("$media/53").watched)
        assertFalse(entries.getValue("https://animeav1.com/media/qa-other/1").watched)
        assertEquals(123L, entries.getValue("$media/1").openedAt)
        assertEquals(30_000L, entries.getValue("$media/1").positionMs)
    }
}
