package com.animeflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

data class Dorama(val slug: String, val title: String, val poster: String = "") {
    val url get() = "${DoramasCatalog.ORIGIN}/doramas/$slug"
}

data class DoramaEpisode(val slug: String, val season: Int, val number: Int) {
    val url get() = "${DoramasCatalog.ORIGIN}/capitulos/$slug-${season}x$number"
}
data class DoramaPublication(val episode: DoramaEpisode, val title: String, val poster: String)
data class DoramaCast(val name: String, val character: String)
data class DoramaDetails(
    val show: Dorama,
    val synopsis: String,
    val year: String,
    val country: String,
    val runtime: Int?,
    val episodeCount: Int?,
    val publishedCount: Int?,
    val status: String,
    val genres: List<String>,
    val labels: List<String>,
    val networks: List<String>,
    val audio: List<String>,
    val subtitles: List<String>,
    val cast: List<DoramaCast>,
    val episodes: List<DoramaEpisode>,
    val sourceId: String,
    internal val scripts: List<String>
) {
    val unpublished get() = publishedCount == 0 || (status == "Próximamente" && episodes.isEmpty())
}

enum class DoramaFilter(val label: String, val parameter: String, val list: String? = null) {
    Genre("Género", "genreSlugs", "listGenres"),
    Network("Productora", "networkSlugs", "listNetworks"),
    Tag("Etiqueta", "labelSlugs", "listLabels"),
    Country("País", "countries", "listCountries"),
    Audio("Idioma", "bylanguages", "listLanguages"),
    Subtitles("Subtítulos", "bysubtitles"),
    Year("Año", "years"),
    Status("Estado", "status");

    val verifyOptions get() = this == Network || this == Year
}

data class DoramaFilterOption(val slug: String, val label: String)

data class DoramaAvailability(
    val upcoming: Boolean = false,
    val airDateMs: Long? = null,
    val audio: List<String> = emptyList(),
    val checked: Boolean = false
)

object DoramasCatalog {
    const val ORIGIN = "https://doramasflix.co"
    private val seriesPath = Regex("/doramas/([a-z0-9-]+)")
    private val episodePath = Regex("/capitulos/([a-z0-9-]+)-(\\d+)x(\\d+)")
    private val checks = Semaphore(3)
    private val detailsCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, DoramaDetails>>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, DoramaDetails>>?) = size > 200
        }
    )
    @Volatile private var similarAction = "c015df4ea30b9c7c14421e44c89a891bd375b72fbb"
    private val filterAvailability = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, Boolean>>(300, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Boolean>>?) = size > 300
        }
    )
    private val availabilityCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<Long, DoramaAvailability>>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, DoramaAvailability>>?) = size > 200
        }
    )

    fun series(url: String): String? = seriesPath.matchEntire(url.removePrefix(ORIGIN))
        ?.groupValues?.get(1)

    fun episode(url: String): DoramaEpisode? {
        val (_, slug, season, number) = episodePath.matchEntire(url.removePrefix(ORIGIN))?.groupValues ?: return null
        return DoramaEpisode(slug, season.toIntOrNull() ?: return null, number.toIntOrNull() ?: return null)
    }

    suspend fun catalog(page: Int, filters: Map<DoramaFilter, DoramaFilterOption> = emptyMap()): List<Dorama> = withContext(Dispatchers.IO) {
        seriesCards(document(catalogUrl(page, filters)))
    }

    private fun catalogUrl(page: Int, filters: Map<DoramaFilter, DoramaFilterOption>): String {
        val parameters = filters.entries.sortedBy { it.key.ordinal }.joinToString("") { (type, option) ->
            "&${type.parameter}=${URLEncoder.encode(option.slug, "UTF-8")}"
        }
        return "$ORIGIN/doramas?page=$page$parameters"
    }

    suspend fun availableFilterOptions(
        filter: DoramaFilter,
        candidates: List<DoramaFilterOption>,
        selected: Map<DoramaFilter, DoramaFilterOption>
    ): List<DoramaFilterOption> = coroutineScope {
        candidates.map { option ->
            async {
                checks.withPermit {
                    val url = catalogUrl(1, selected + (filter to option))
                    val now = System.currentTimeMillis()
                    val cached = filterAvailability[url]?.takeIf { now - it.first < 15 * 60_000 }
                    val available = cached?.second ?: withContext(Dispatchers.IO) {
                        val page = document(url)
                        when {
                            seriesCards(page).isNotEmpty() -> true
                            page.select("p").any { it.text().equals("No hay doramas disponibles.", ignoreCase = true) } -> false
                            else -> error("Doramasflix no devolvió resultados verificables")
                        }.also { filterAvailability[url] = now to it }
                    }
                    option.takeIf { available }
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun filters(): Map<DoramaFilter, List<DoramaFilterOption>> = withContext(Dispatchers.IO) {
        val payload = flight(document("$ORIGIN/doramas"))
        DoramaFilter.entries.associateWith { filter ->
            when (filter) {
                DoramaFilter.Subtitles -> listOf(DoramaFilterOption("es", "Español"), DoramaFilterOption("en", "Inglés"), DoramaFilterOption("pt", "Portugués"))
                DoramaFilter.Year -> (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1 downTo 1950)
                    .map { DoramaFilterOption(it.toString(), it.toString()) }
                DoramaFilter.Status -> listOf(
                    DoramaFilterOption("emision", "En emisión"), DoramaFilterOption("finished", "Finalizado"),
                    DoramaFilterOption("commingSoon", "Próximamente"), DoramaFilterOption("pause", "En pausa"),
                    DoramaFilterOption("uploading", "Publicando")
                )
                else -> array(payload, checkNotNull(filter.list)).objects().mapNotNull { item ->
                    val slug = item.optString("slug")
                    val name = item.optString("name")
                    if (slug.isBlank() || name.isBlank()) null else DoramaFilterOption(slug, name)
                }.distinctBy { it.slug }.let { values ->
                    if (filter == DoramaFilter.Network) values else values.sortedBy { it.label.lowercase() }
                }
            }
        }.also { options -> check(options.any { (filter, values) -> filter.list != null && values.isNotEmpty() }) }
    }

    suspend fun search(query: String, filters: Map<DoramaFilter, DoramaFilterOption> = emptyMap()): List<Dorama> = withContext(Dispatchers.IO) {
        val term = URLEncoder.encode(query.trim(), "UTF-8")
        val matches = seriesCards(document("$ORIGIN/buscar?q=$term"))
        if (filters.isEmpty()) return@withContext matches
        coroutineScope {
            matches.map { dorama ->
                async { checks.withPermit { dorama.takeIf { matchesFilters(document(it.url), it.slug, filters) } } }
            }.awaitAll().filterNotNull()
        }
    }

    internal fun matchesFilters(page: Document, slug: String, filters: Map<DoramaFilter, DoramaFilterOption>): Boolean {
        val payload = flight(page)
        val info = objectsWithSlug(payload, slug)
            .firstOrNull { it.optString("slug") == slug && it.optJSONArray("seasons") != null }
            ?: return false
        fun hasTaxonomy(path: String, value: String) = page.select("a[href]").any {
            it.attr("href").removePrefix(ORIGIN) == "/$path/$value"
        }
        val seasons = info.optJSONArray("seasons").objects()
        return filters.all { (type, option) ->
            val value = option.slug
            when (type) {
                DoramaFilter.Genre -> hasTaxonomy("generos", value) || info.optJSONArray("genres").objects().any { it.optString("slug") == value }
                DoramaFilter.Network -> hasTaxonomy("productoras", value) || info.optJSONArray("networks").objects().any { it.optString("slug") == value }
                DoramaFilter.Tag -> hasTaxonomy("etiquetas", value) || info.optJSONArray("labels").objects().any { it.optString("slug") == value }
                DoramaFilter.Country -> info.optString("country") == value
                DoramaFilter.Audio -> info.optJSONArray("langs").objects().any { it.optString("slug") == value }
                DoramaFilter.Subtitles -> info.optJSONArray("subtitles_available")?.let { values ->
                    (0 until values.length()).any { values.optString(it) == value }
                } == true
                DoramaFilter.Year -> info.optString("first_air_date").substringBefore('-') == value || info.optString("year") == value
                DoramaFilter.Status -> if (value == "finished") info.optBoolean("isFinish") || info.optString("status") == value
                    else seasons.any { it.optBoolean(value) } || info.optString("status") == value
            }
        }
    }

    private fun objectsWithSlug(payload: String, slug: String): Sequence<JSONObject> = sequence {
        val marker = "\"slug\":${JSONObject.quote(slug)}"
        val objects = ArrayDeque<Int>()
        var inString = false
        var escaped = false
        payload.forEachIndexed { index, char ->
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else when (char) {
                '{' -> objects.addLast(index)
                '}' -> objects.removeLastOrNull()
                '"' -> {
                    if (objects.isNotEmpty() && payload.startsWith(marker, index)) {
                        val candidate = runCatching {
                            JSONTokener(payload.substring(objects.last())).nextValue() as? JSONObject
                        }.getOrNull()
                        if (candidate != null) yield(candidate)
                    }
                    inString = true
                }
            }
        }
    }

    private fun seriesCards(page: Document): List<Dorama> =
        page.select("a[href][aria-label]").mapNotNull { link ->
            val slug = series(link.attr("href")) ?: return@mapNotNull null
            val title = link.attr("aria-label").removePrefix("Ver ").trim()
            val poster = link.selectFirst("img[src]")?.absUrl("src").orEmpty()
            Dorama(slug, title, poster).takeIf { title.isNotEmpty() }
        }.distinctBy { it.slug }

    suspend fun details(dorama: Dorama): DoramaDetails = checks.withPermit {
        val now = System.currentTimeMillis()
        detailsCache[dorama.slug]?.takeIf { now - it.first < 300_000 }?.let { return@withPermit it.second }
        withContext(Dispatchers.IO) { readDetails(document(dorama.url), dorama) }
            .also { detailsCache[dorama.slug] = now to it }
    }

    suspend fun episodes(dorama: Dorama): List<DoramaEpisode> = details(dorama).episodes

    internal fun readDetails(page: Document, dorama: Dorama): DoramaDetails {
        val info = objectsWithSlug(flight(page), dorama.slug)
            .firstOrNull { it.optJSONArray("seasons") != null }
        val title = page.selectFirst("h1")?.text()?.takeIf(String::isNotBlank) ?: dorama.title
        fun field(key: String) = info?.optString(key)?.takeUnless { it.startsWith("$") || it == "null" }.orEmpty()
        fun number(key: String): Int? = info?.optInt(key, -1)?.takeIf { it >= 0 }
        fun taxonomy(key: String, path: String): List<String> {
            val tags = info?.optJSONArray(key).objects().mapNotNull { it.optString("name").takeIf(String::isNotBlank) }
            return (tags + page.select("a[href^=/$path/]").map { it.text() }.filter(String::isNotBlank)).distinct()
        }
        val synopsis = field("overview_es").ifBlank {
            page.select("h2,h3,h4").firstOrNull { it.text().equals("Sinopsis", true) }
                ?.nextElementSibling()?.text().orEmpty()
        }.ifBlank { field("overview") }
        val seasons = info?.optJSONArray("seasons").objects()
        val status = when {
            field("status") == "finished" || info?.optBoolean("isFinish") == true -> "Finalizado"
            field("status") == "commingSoon" || seasons.isNotEmpty() && seasons.all { it.optBoolean("commingSoon") } -> "Próximamente"
            field("status") == "pause" || seasons.any { it.optBoolean("pause") } -> "En pausa"
            field("status") == "emision" || seasons.any { it.optBoolean("emision") } -> "En emisión"
            field("status") == "uploading" || seasons.any { it.optBoolean("uploading") } -> "Publicando"
            else -> ""
        }
        val country = field("country").replace('-', ' ').replaceFirstChar { it.uppercase() }
            .let { if (it == "Corea") "Corea del Sur" else it }
        val subs = info?.optJSONArray("subtitles_available")
        val episodes = page.select("a[href]").mapNotNull { episode(it.attr("href")) }
            .filter { it.slug == dorama.slug }.distinct().sortedWith(compareBy(DoramaEpisode::season, DoramaEpisode::number))
        val poster = page.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.startsWith("https://") } ?: posterUrl(field("poster_path")).ifBlank { dorama.poster }
        return DoramaDetails(
            Dorama(dorama.slug, title, poster), synopsis, field("first_air_date").substringBefore('-').ifBlank { field("year") },
            country, number("episode_time")?.takeIf { it > 0 }, number("number_of_episodes"), number("number_of_episodes_online"),
            status, taxonomy("genres", "generos"), taxonomy("labels", "etiquetas"), taxonomy("networks", "productoras"),
            info?.optJSONArray("langs").objects().mapNotNull { it.optString("name").takeIf(String::isNotBlank) },
            if (subs == null) emptyList() else (0 until subs.length()).map { index ->
                when (val code = subs.optString(index)) { "es" -> "Español"; "en" -> "Inglés"; "pt" -> "Portugués"; else -> code.uppercase() }
            }.filter(String::isNotBlank).distinct(),
            info?.optJSONArray("cast").objects().mapNotNull { person ->
                person.optString("name").takeIf(String::isNotBlank)?.let { DoramaCast(it, person.optString("character")) }
            }.take(12),
            episodes, field("_id"), page.select("script[src]").map { it.attr("src") }
                .filter { it.matches(Regex("/_next/static/chunks/[a-zA-Z0-9._-]+\\.js")) }.distinct()
        )
    }

    suspend fun similar(details: DoramaDetails): List<Dorama> = withContext(Dispatchers.IO) {
        val id = details.sourceId
        check(id.matches(Regex("[a-f0-9]{24}"))) { "La ficha no incluye el identificador de Doramasflix" }
        fun query(action: String): List<Dorama> {
            val response = Jsoup.connect(details.show.url).method(org.jsoup.Connection.Method.POST)
                .header("Next-Action", action).header("Accept", "text/x-component")
                .header("Content-Type", "text/plain;charset=UTF-8").ignoreContentType(true)
                .followRedirects(false).timeout(12_000).maxBodySize(1_000_000)
                .requestBody(JSONArray().put(JSONObject().put("dorama_id", id).put("limit", 12)).toString())
                .execute()
            check(response.statusCode() == 200)
            return parseSimilar(response.body(), details.show.slug)
        }
        val action = similarAction
        val initial = runCatching { query(action) }
        if (initial.isSuccess) return@withContext initial.getOrThrow()
        val pattern = Regex("\"([a-f0-9]{40})\",[^;]{0,180}\"getSimilarsDoramas\"")
        val refreshed = details.scripts.asReversed().firstNotNullOfOrNull { path ->
            runCatching {
                val script = Jsoup.connect("$ORIGIN$path").ignoreContentType(true).timeout(4_000)
                    .maxBodySize(2_000_000).execute().body()
                pattern.find(script)?.groupValues?.get(1)
            }.getOrNull()
        } ?: throw initial.exceptionOrNull()!!
        query(refreshed).also { similarAction = refreshed }
    }

    internal fun parseSimilar(response: String, currentSlug: String): List<Dorama> {
        val reference = Regex("\"a\":\"\\$@([a-f0-9]+)\"").find(response)?.groupValues?.get(1)
        val prefix = "${reference ?: "1"}:"
        val items = response.lineSequence().firstOrNull { it.startsWith("${prefix}[") }
            ?.removePrefix(prefix)?.let(::JSONArray) ?: error("Respuesta de similares inválida")
        return items.objects().mapNotNull { item ->
            val slug = item.optString("slug")
            if (slug == currentSlug || series("/doramas/$slug") == null || item.optBoolean("isTVShow")) return@mapNotNull null
            val title = item.optString("name_es").takeUnless { it.isBlank() || it == "null" } ?: item.optString("name")
            if (title.isBlank()) return@mapNotNull null
            Dorama(slug, title, posterUrl(item.optString("poster_path")))
        }.distinctBy { it.slug }
    }

    private fun posterUrl(path: String) = if (path.matches(Regex("/[a-zA-Z0-9._-]+"))) "https://image.tmdb.org/t/p/w342$path" else ""

    suspend fun recent(): List<DoramaPublication> = withContext(Dispatchers.IO) {
        document("$ORIGIN/ultimos-episodios").select("a[href]").mapNotNull { link ->
            val episode = episode(link.attr("href")) ?: return@mapNotNull null
            val image = link.selectFirst("img[src]")
            val title = link.text().trim().ifBlank { image?.attr("alt").orEmpty().trim() }
                .removeSuffix(" ${episode.season}x${episode.number}").trim()
                .ifBlank { episode.slug.replace('-', ' ').replaceFirstChar { it.uppercase() } }
            DoramaPublication(episode, title, image?.absUrl("src").orEmpty())
        }.distinctBy { it.episode }
    }

    suspend fun availability(episode: DoramaEpisode): DoramaAvailability = checks.withPermit {
        val now = System.currentTimeMillis()
        val cached = availabilityCache.get(episode.url)
        if (cached != null && now - cached.first < 300_000) return@withPermit cached.second
        val result = withContext(Dispatchers.IO) { readAvailability(document(episode.url), now) }
        if (result.checked) availabilityCache[episode.url] = now to result
        result
    }

    private fun flight(page: Document): String =
        page.select("script").asSequence().mapNotNull { script ->
            val data = script.data().substringAfter("self.__next_f.push(", "").trim().removeSuffix(")")
            if (!data.startsWith("[1,")) null else runCatching { JSONArray(data).getString(1) }.getOrNull()
        }.joinToString("\n")

    private fun array(payload: String, name: String): JSONArray? =
        runCatching { JSONTokener(payload.substringAfter("\"$name\":", "")).nextValue() as? JSONArray }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    internal fun readAvailability(page: Document, now: Long): DoramaAvailability {
        val payload = flight(page)
        val airDate = Regex("\"air_date\":\"?(\\d{13})").findAll(payload.substringBefore("\"initialLinks\""))
            .lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
        val upcoming = page.select("h2,h3,h4").any { it.text().contains("aún no ha sido emitido", ignoreCase = true) } ||
            (airDate != null && airDate > now)
        if (upcoming) return DoramaAvailability(upcoming = true, airDateMs = airDate?.takeIf { it > now }, checked = true)

        val links = array(payload, "initialLinks") ?: return DoramaAvailability()
        val audio = array(payload, "listLanguages").objects() + array(payload, "langs").objects()
        val languages = audio.associate { it.optString("code_flix") to it.optString("name").trim() }
        val available = links.objects().filter {
            it.optString("link").isNotBlank()
        }.map {
            val language = languages[it.optString("lang")].orEmpty()
            when (language.lowercase()) {
                "coreano" -> "Coreano · sub. español"
                "latino" -> "Audio latino"
                "" -> "Audio sin identificar"
                else -> language
            }
        }.distinct()
        return DoramaAvailability(audio = available, checked = true)
    }

    private fun document(url: String): Document = Jsoup.connect(url).timeout(15_000)
        .maxBodySize(2_000_000).get()
}
