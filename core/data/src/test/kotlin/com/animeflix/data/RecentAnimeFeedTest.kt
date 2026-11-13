package com.animeflix.data

import com.animeflix.model.Anime
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RecentAnimeFeedTest {
    @Test fun readsRealPublicEpisodeCardsCapturedOnNovember13() {
        val html = checkNotNull(javaClass.getResource("/recent-episodes-2026-11-13.html")).readText()
        val items = parseRecentAnime(html)
        assertEquals(listOf("av1:tetsuryou-meet-with-tetsudou-musume", "av1:hyouken-no-majutsushi-ga-sekai-wo-suberu-ii",
            "av1:tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru"), items.map { it.id })
        assertEquals("Tetsuryou! Meet with Tetsudou Musume", items.first().title)
        assertEquals("https://cdn.animeav1.com/covers/4486.jpg", items.first().posterUrl)
    }

    private fun article(slug: String, title: String, number: String = "1", image: String = "https://cdn.animeav1.com/thumbnails/42.jpg") =
        """<article><img src="$image"><header><div>$title</div></header><a href="/media/$slug/$number">Ver</a></article>"""
    private fun feed(body: String) = "<section><header><h2>Episodios</h2><p>Recientemente Actualizado</p></header>$body</section>"
    private fun anime(id: String, title: String) = Anime("av1:$id", title, "Descripción completa", favorite = true)

    @Test fun publishedOrderIgnoresFeaturedAndRecommendationsAndDeduplicatesSeries() {
        val html = article("featured", "Destacado") + feed(article("zeta", "Zeta &amp; amigos", "3") +
            article("alpha", "Alpha", "2.5") + article("zeta", "Zeta &amp; amigos", "2")) +
            "<section><h2>Recomendados</h2>${article("other", "Otra")}</section>"
        val items = parseRecentAnime(html)
        assertEquals(listOf("av1:zeta", "av1:alpha"), items.map { it.id })
        assertEquals("Zeta & amigos", items.first().title)
        assertEquals("https://animeav1.com/media/zeta", items.first().sourceUrl)
        assertEquals("https://cdn.animeav1.com/covers/42.jpg", items.first().posterUrl)
    }

    @Test fun recentSeriesComeBeforeAlphabeticalRemainderWithoutChangingFavoritesOrMetadata() {
        val items = listOf(anime("alpha", "Alpha"), anime("beta", "Beta"), anime("zeta", "Zeta"))
        val ordered = prioritizeRecent(items, listOf("av1:zeta", "av1:missing"))
        assertEquals(listOf("Zeta", "Alpha", "Beta"), ordered.map { it.title })
        assertEquals(items.toSet(), ordered.toSet())
        // The next successful feed replaces its ranking: an old update is no longer pinned.
        assertEquals(listOf("Beta", "Alpha", "Zeta"), prioritizeRecent(items, listOf("av1:beta")).map { it.title })
        assertEquals(items, prioritizeRecent(items.reversed(), emptyList()))
    }

    @Test fun invalidLinksAndUntrustedCoverHostsAreIgnored() {
        val html = feed(article("safe", "Serie", image = "https://evil.invalid/thumbnails/42.jpg") +
            """<article><header>Impostor</header><a href="https://animeav1.com.evil.invalid/media/bad/1">Ver</a></article>""")
        val items = parseRecentAnime(html)
        assertEquals(1, items.size)
        assertEquals("", items.single().posterUrl)
    }

    @Test(expected = IOException::class) fun changedSourceMustNotEraseCachedOrder() {
        parseRecentAnime("<section><h2>Recomendados</h2>${article("other", "Otra")}</section>")
    }

    @Test(expected = IOException::class) fun emptyFeedIsNotASuccessfulRefresh() {
        parseRecentAnime(feed(""))
    }
}
