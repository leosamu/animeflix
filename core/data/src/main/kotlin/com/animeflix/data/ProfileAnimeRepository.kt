package com.animeflix.data

import android.content.Context
import androidx.room.Room
import com.animeflix.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ProfileSyncState(val running: Boolean = false, val message: String? = null, val lastSuccess: Long? = null)

/** A fixed profile scope. Existing player callbacks can never write into a newly selected profile. */
class ProfileAnimeRepository private constructor(
    private val context: Context, private val local: LocalAnimeRepository,
    private val entries: ProfileEntryDao?, private val account: AccountState,
    private val accounts: AccountManager
) : AnimeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val mutableSync = MutableStateFlow(ProfileSyncState())
    val syncState = mutableSync.asStateFlow()
    private val preferences = context.getSharedPreferences("library_preferences", Context.MODE_PRIVATE)
    private val legacyPreference = MutableStateFlow(preferences.getBoolean("hideCaughtUp", false))
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    init { scope.launch { for (request in syncRequests) sync() } }
    private fun observeEntries(): Flow<List<ProfileEntry>> = entries?.observe() ?: flowOf(emptyList())
    fun observeHideCaughtUp(): Flow<Boolean> = if (entries == null) legacyPreference else observeEntries().map { rows ->
        rows.find { it.kind == "preference" && it.itemKey == "hideCaughtUp" }?.let { runCatching { JSONObject(it.value).optBoolean("enabled") }.getOrDefault(false) } ?: false
    }
    suspend fun setHideCaughtUp(value: Boolean) {
        if (entries == null) { preferences.edit().putBoolean("hideCaughtUp", value).apply(); legacyPreference.value = value }
        else put("preference", "hideCaughtUp", JSONObject().put("enabled", value))
    }
    override fun observeCatalog(): Flow<List<Anime>> = if (entries == null) local.observeCatalog() else combine(local.observeCatalog(), observeEntries()) { catalog, rows ->
        val favorites = rows.filter { it.kind == "favorite" }.mapNotNull { row -> runCatching {
            val json = JSONObject(row.value)
            Anime(row.itemKey, json.getString("title"), json.optString("synopsis"), json.getBoolean("enabled"), json.optString("posterUrl"), json.optString("sourceUrl"))
        }.getOrNull() }.associateBy { it.id }
        (catalog.map { it.copy(favorite = favorites[it.id]?.favorite ?: false) } + favorites.values.filter { favorite -> catalog.none { it.id == favorite.id } }).sortedBy { it.title }
    }
    suspend fun cacheCatalog(items: List<Anime>) = local.cacheCatalog(items)
    override suspend fun seedDemo() = local.seedDemo()
    override suspend fun setFavorite(id: String, favorite: Boolean) {
        if (entries == null) local.setFavorite(id, favorite) else {
            val anime = observeCatalog().first().find { it.id == id } ?: error("El anime no está disponible.")
            put("favorite", id, JSONObject().put("enabled", favorite).put("title", anime.title).put("synopsis", anime.synopsis)
                .put("posterUrl", anime.posterUrl).put("sourceUrl", anime.sourceUrl))
        }
    }
    fun observeInventories() = local.observeInventories()
    suspend fun cacheInventory(inventory: EpisodeInventory) = local.cacheInventory(inventory)
    fun observeWatchHistory(): Flow<List<WatchEntry>> = if (entries == null) local.observeWatchHistory() else observeEntries().map { rows ->
        rows.filter { it.kind in setOf("opened", "watched", "progress") && AnimeAv1Episodes.isEpisodeUrl(it.itemKey) }
            .groupBy { it.itemKey }.map { (url, fields) ->
                fun value(kind: String) = fields.find { it.kind == kind }?.let { runCatching { JSONObject(it.value) }.getOrNull() }
                val duration = (value("progress")?.optLong("durationMs") ?: 0).coerceIn(0, 86_400_000)
                WatchEntry(url, url.substringBeforeLast('/'), url.substringAfterLast('/'),
                    (value("opened")?.optLong("openedAt") ?: 0).coerceAtLeast(0), value("watched")?.optBoolean("watched") ?: false,
                    (value("progress")?.optLong("positionMs") ?: 0).coerceIn(0, duration), duration)
            }.sortedWith(compareByDescending<WatchEntry> { it.openedAt }.thenBy { it.episodeUrl })
    }
    suspend fun recordOpened(url: String, time: Long = System.currentTimeMillis()) {
        require(AnimeAv1Episodes.isEpisodeUrl(url) && time > 0)
        if (entries == null) local.recordOpened(url, time) else put("opened", url, JSONObject().put("openedAt", time))
    }
    suspend fun markWatched(url: String, watched: Boolean) {
        require(AnimeAv1Episodes.isEpisodeUrl(url))
        if (entries == null) local.markWatched(url, watched) else put("watched", url, JSONObject().put("watched", watched))
    }
    suspend fun saveProgress(url: String, positionMs: Long, durationMs: Long) {
        require(AnimeAv1Episodes.isEpisodeUrl(url) && durationMs in 60_000..86_400_000 && positionMs in 0..durationMs)
        if (entries == null) local.saveProgress(url, positionMs, durationMs)
        else put("progress", url, JSONObject().put("positionMs", positionMs).put("durationMs", durationMs))
    }
    suspend fun markPrevious(episodes: List<Episode>, target: Episode) {
        if (entries == null) { local.markPrevious(episodes, target); return }
        require(AnimeAv1Episodes.isEpisodeUrl(target.pageUrl) && target.number == target.pageUrl.substringAfterLast('/'))
        val media = target.pageUrl.substringBeforeLast('/')
        val previous = episodes.distinctBy { it.pageUrl }.onEach {
            require(AnimeAv1Episodes.isEpisodeUrl(it.pageUrl) && it.pageUrl.substringBeforeLast('/') == media && it.number == it.pageUrl.substringAfterLast('/'))
        }.filter { it.number.toBigDecimal() < target.number.toBigDecimal() }
        entries.putAll(previous.map { ProfileEntry("watched", it.pageUrl, JSONObject().put("watched", true).toString()) })
        requestSync()
    }
    private suspend fun put(kind: String, key: String, value: JSONObject) {
        checkNotNull(entries).put(ProfileEntry(kind, key, value.toString()))
        requestSync()
    }
    private fun requestSync() { syncRequests.trySend(Unit) }
    suspend fun sync() {
        if (entries == null || account.preview) return
        syncMutex.withLock {
            // Stop background work from a scope that was signed out or switched away.
            if (accounts.state.value.accountId != account.accountId || accounts.state.value.selected?.id != account.selected?.id) return
            mutableSync.value = mutableSync.value.copy(running = true, message = null)
            try {
                val accountId = checkNotNull(account.accountId)
                val profileId = checkNotNull(account.selected).id
                entries.pending().forEach { entry ->
                    accounts.push(accountId, profileId, entry)
                    entries.acknowledge(entry.kind, entry.itemKey, entry.revision)
                }
                entries.receive(accounts.pull(accountId, profileId))
                mutableSync.value = ProfileSyncState(lastSuccess = System.currentTimeMillis())
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableSync.value = mutableSync.value.copy(running = false, message = if (failure is CloudException) failure.message else "Sin conexión. Tus cambios están guardados en este perfil y se sincronizarán al reintentar.")
            }
        }
    }
    companion object {
        private val instances = mutableMapOf<String, ProfileAnimeRepository>()
        @Synchronized fun create(context: Context): ProfileAnimeRepository {
            val app = context.applicationContext
            val manager = AccountManager.create(app)
            val account = manager.state.value
            val profile = account.selected
            val key = if (profile == null) "legacy" else "${account.accountId}:${profile.id}"
            return instances.getOrPut(key) {
                if (profile == null) ProfileAnimeRepository(app, LocalAnimeRepository.create(app), null, account, manager)
                else {
                    requireNotNull(account.accountId)
                    val name = "profile-" + java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".db"
                    val db = Room.databaseBuilder(app, AnimeDatabase::class.java, name).addMigrations(LocalAnimeRepository.MIGRATION_5_6).build()
                    ProfileAnimeRepository(app, LocalAnimeRepository.fromDatabase(db), db.profileEntryDao(), account, manager)
                }
            }
        }
    }
}
