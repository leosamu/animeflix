package com.animeflix.data

import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DoramasCatalogTest {
    private val now = 1_800_000_000_000L

    @Test
    fun detailsKeepEpisodesAndRecognizeUnpublishedSeries() {
        val page = pageWith("""
            {"name":"Pendiente","slug":"pendiente","country":"corea","overview_es":"Una sinopsis",
             "number_of_episodes_online":0,"number_of_episodes":10,"seasons":[{"commingSoon":true}]}
        """.trimIndent(), """<h1>Pendiente</h1><a href="/capitulos/pendiente-1x1">Episodio futuro</a>""")
        val details = DoramasCatalog.readDetails(page, Dorama("pendiente", "Pendiente"))
        assertTrue(details.unpublished)
        assertEquals("Una sinopsis", details.synopsis)
        assertEquals("Corea del Sur", details.country)
        assertEquals(listOf(DoramaEpisode("pendiente", 1, 1)), details.episodes)
    }

    @Test
    fun parsesSimilarTitlesUsingTheProvidersOrderAndReference() {
        val response = """
            0:{"a":"$@2"}
            1:{}
            2:[{"slug":"vincenzo","name":"Vincenzo"},{"slug":"otro","name":"Other","name_es":"Otro","poster_path":"/poster.jpg"}]
        """.trimIndent()
        val result = DoramasCatalog.parseSimilar(response, "vincenzo")
        assertEquals(listOf(Dorama("otro", "Otro", "https://image.tmdb.org/t/p/w342/poster.jpg")), result)
    }

    @Test
    fun combinesCatalogFiltersWithSearchResults() {
        val page = pageWith("""
            [{"_id":"related","slug":"otro"},{"_id":"v","slug":"vincenzo","country":"corea","first_air_date":"2021-02-20",
              "isFinish":true,"seasons":[{}],"genres":[{"slug":"accion"}],"networks":[{"slug":"netflix"}],
              "langs":[{"slug":"coreano"}],"subtitles_available":["es"]}]
        """.trimIndent(), """<a href="/etiquetas/leyes">Leyes</a>""")
        val filters = mapOf(
            DoramaFilter.Country to "corea", DoramaFilter.Genre to "accion", DoramaFilter.Network to "netflix",
            DoramaFilter.Tag to "leyes", DoramaFilter.Audio to "coreano", DoramaFilter.Subtitles to "es",
            DoramaFilter.Year to "2021", DoramaFilter.Status to "finished"
        ).mapValues { DoramaFilterOption(it.value, it.value) }

        assertTrue(DoramasCatalog.matchesFilters(page, "vincenzo", filters))
        assertFalse(DoramasCatalog.matchesFilters(page, "vincenzo",
            filters + (DoramaFilter.Country to DoramaFilterOption("japon", "Japón"))))
    }

    @Test
    fun audioIncludesNewServersEvenBeforeTheSiteListsTheirNames() {
        val page = pageWith("""
            {"listLanguages":[{"code_flix":"1","name":"Coreano"},{"code_flix":"2","name":"Latino"}],
             "listServers":[{"code_flix":"d","name":"Dood"}],
             "initialLinks":[{"server":"d","lang":"1","link":"https://player.example/one"},
             {"server":"p","lang":"2","link":"https://player.example/two"}]}
        """.trimIndent())

        assertEquals(listOf("Coreano · sub. español", "Audio latino"), DoramasCatalog.readAvailability(page, now).audio)
    }

    @Test
    fun upcomingAnnouncementRemainsVisibleAfterEstimatedDate() {
        val page = pageWith(
            """{"air_date":"1794891600000","initialLinks":[]}""",
            "<h4>Este episodio aún no ha sido emitido.</h4>"
        )

        val availability = DoramasCatalog.readAvailability(page, now)
        assertTrue(availability.upcoming)
        assertNull(availability.airDateMs)
    }

    @Test
    fun keepsEveryLanguageReturnedByPublishedLinks() {
        val page = pageWith("""
            {"listLanguages":[{"code_flix":"1","name":"Japonés"},{"code_flix":"2","name":"Chino"},
             {"code_flix":"3","name":"Tagalo"}],
             "listServers":[{"code_flix":"d","name":"Dood"}],
             "initialLinks":[{"server":"d","lang":"1","link":"https://player.example/one"},
             {"server":"d","lang":"2","link":"https://player.example/two"},
             {"server":"d","lang":"3","link":"https://player.example/three"}]}
        """.trimIndent())

        assertEquals(listOf("Japonés", "Chino", "Tagalo"), DoramasCatalog.readAvailability(page, now).audio)
    }

    private fun pageWith(payload: String, announcement: String = "") = Jsoup.parse(
        "$announcement<script>self.__next_f.push(${JSONArray().put(1).put(payload)})</script>"
    )
}
