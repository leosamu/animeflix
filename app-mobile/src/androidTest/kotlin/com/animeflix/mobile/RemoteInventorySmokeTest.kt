package com.animeflix.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.data.LocalAnimeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteInventorySmokeTest {
    @Test fun readsActualLatestEpisodePublicationAndCachesIt() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val url = "https://animeav1.com/media/omae-gotoki-ga-maou-ni-kateru-to-omouna-to-yuusha-party-wo-tsuihou-sareta-node-outo-de-kimama-ni-kurashitai"
        val inventory = AnimeAv1Episodes().inventory(url)
        assertTrue(inventory.episodes.isNotEmpty())
        assertNotNull(inventory.latestPublishedAt)
        val repository = LocalAnimeRepository.create(context)
        repository.cacheInventory(inventory)
        val cached = repository.observeInventories().first().single { it.mediaUrl == url }
        assertEquals(inventory, cached)
    }
}
