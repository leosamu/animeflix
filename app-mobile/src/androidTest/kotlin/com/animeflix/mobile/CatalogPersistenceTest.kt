package com.animeflix.mobile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.model.Anime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogPersistenceTest {
    @Test fun refreshingMetadataPreservesFavorite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa")) { "Las pruebas deben usar la instalación separada QA" }
        val repository = LocalAnimeRepository.create(context)
        val anime = Anime("qa-cache", "Prueba de caché", "Descripción inicial")
        repository.cacheCatalog(listOf(anime))
        repository.setFavorite(anime.id, true)
        repository.cacheCatalog(listOf(anime.copy(title = "Título actualizado", synopsis = "Nueva descripción")))
        val cached = repository.observeCatalog().first().single { it.id == anime.id }
        assertTrue(cached.favorite)
        assertEquals("Título actualizado", cached.title)
        assertEquals("Nueva descripción", cached.synopsis)
    }
}
