package com.animeflix.data

import com.animeflix.model.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder

data class CatalogPage(val items: List<Anime>, val hasMore: Boolean)

/** Reads public catalog HTML only. Does not execute scripts or resolve video hosts. */
class AnimeAv1Catalog {
    suspend fun fetch(page: Int = 1, search: String = ""): CatalogPage = withContext(Dispatchers.IO) {
        require(page > 0)
        val query = URLEncoder.encode(search.trim(), "UTF-8")
        val url = "https://animeav1.com/catalogo?page=$page&search=$query"
        val document = Jsoup.connect(url).userAgent("AnimeFlix/0.2 (Android catalog client)")
            .timeout(20_000).maxBodySize(3_000_000).get()
        val items = document.select("article").mapNotNull { article ->
            val link = article.selectFirst("a[href^=/media/]") ?: return@mapNotNull null
            val title = article.selectFirst("h3")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val source = link.absUrl("href")
            if (!source.startsWith("https://animeav1.com/media/")) return@mapNotNull null
            val poster = article.selectFirst("img")?.absUrl("src").orEmpty()
                .takeIf { it.startsWith("https://cdn.animeav1.com/") }.orEmpty()
            Anime("av1:" + source.substringAfterLast('/'), title,
                article.selectFirst("p")?.text().orEmpty().ifBlank { "Sin sinopsis disponible." },
                posterUrl = poster, sourceUrl = source)
        }.distinctBy { it.id }
        if (items.isEmpty() && !document.text().contains("Resultados", ignoreCase = true)) {
            throw IOException("El catálogo no presenta la estructura esperada")
        }
        CatalogPage(items, items.size >= 20)
    }
}
