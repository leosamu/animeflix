package com.animeflix.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animeflix.data.AnimeAv1Episodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Network smoke test: failures may also indicate site availability changes. */
@RunWith(AndroidJUnit4::class)
class RemoteEpisodeSmokeTest {
    @Test fun publishedEpisodeProvidesServers() = runBlocking {
        val source = AnimeAv1Episodes()
        val episodes = source.episodes("https://animeav1.com/media/one-piece")
        assertTrue("Lista pública de episodios vacía", episodes.isNotEmpty())
        val servers = source.servers("https://animeav1.com/media/one-piece/1")
        assertTrue("No se encontraron reproductores publicados", servers.isNotEmpty())
    }
}
