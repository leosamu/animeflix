package com.animeflix.data

import org.junit.Assert.*
import org.junit.Test

class AnimeAv1EpisodesTest {
    private val media = "https://animeav1.com/media/example"

    @Test fun collectsAllPublishedEpisodesAndSortsNumbers() {
        val html = """<script>episodes:[{id:1,number:10},{id:2,number:2},{id:3,number:2.5}]</script>
            <a href="/media/example/2">2</a><a href="/media/other/4">other</a>
            <a href="https://evil.invalid/media/example/3">bad</a>"""
        val result = AnimeAv1Episodes.parseEpisodes(html, media)
        assertEquals(listOf("2", "2.5", "10"), result.map { it.number })
        assertTrue(result.all { it.pageUrl.startsWith("$media/") })
    }

    @Test fun onlyAcceptsPublishedHttpsEmbedsAndKeepsLanguage() {
        val html = """<script>embeds:{SUB:[{server:"HLS",url:"https://player.example/play/1"}],LAT:[{server:"HLS",url:"https://player.example/play/2"}]},downloads:{SUB:[{server:"File",url:"https://download.example/file"}]}</script>
            <iframe src="https://player.example/play/1"></iframe><iframe src="javascript:alert(1)"></iframe>"""
        val result = AnimeAv1Episodes.parseServers(html, "$media/1")
        assertEquals(listOf("SUB · HLS", "LAT · HLS"), result.map { it.name })
        assertEquals(2, result.size)
        assertFalse(AnimeAv1Episodes.isHttpsUrl("https://user:password@example.com/"))
        assertFalse(AnimeAv1Episodes.isEpisodeUrl("https://animeav1.com.evil.invalid/media/example/1"))
    }

    @Test fun supportsPlainIframeAndEmptyEpisodeList() {
        assertEquals(1, AnimeAv1Episodes.parseServers("""<iframe src="https://player.example/embed"></iframe>""", "$media/1").size)
        assertTrue(AnimeAv1Episodes.parseEpisodes("<h2>Episodios</h2>", media).isEmpty())
    }
}
