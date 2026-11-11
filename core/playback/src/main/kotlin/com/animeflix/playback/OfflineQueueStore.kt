package com.animeflix.playback

import android.app.DownloadManager
import android.content.Context
import androidx.work.*
import com.animeflix.data.*
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Stable IDs and ownership survive process death. No stream URLs, cookies or headers in this index. */
internal class OfflineQueueStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline_queue_v2", Context.MODE_PRIVATE)
    private fun owner() = AccountManager.create(context).state.value.let { state ->
        state.selected?.let { "${state.accountId}:${it.id}" }?.takeIf { state.accountId != null }
    }
    suspend fun enqueue(url: String, title: String, selected: PlaybackServer, metered: Boolean): Long = withContext(Dispatchers.IO) {
        require(AnimeAv1Episodes.isEpisodeUrl(url) && DownloadOriginPolicy.provider(selected) != null)
        val owner = checkNotNull(owner()) { "Selecciona un perfil antes de descargar." }
        val id = synchronized(lock) {
            records().firstOrNull { it.getString("owner") == owner && it.getString("episode") == url && it.optString("state") != "failed" }?.getLong("id") ?: run {
                val id = -(maxOf(System.currentTimeMillis(), prefs.getLong("sequence", 0) + 1))
                val data = JSONObject().put("id", id).put("owner", owner).put("episode", url).put("title", title.take(160))
                    .put("server", selected.embedUrl).put("serverName", selected.name.take(100)).put("metered", metered)
                    .put("state", "queued").put("bytes", 0).put("total", -1).put("percent", -1)
                check(prefs.edit().putLong("sequence", -id).putString(id.toString(), data.toString()).commit())
                id
            }
        }
        kick(context)
        id
    }
    fun records(): List<JSONObject> = synchronized(lock) {
        prefs.all.filterKeys { it.startsWith("-") }.values.mapNotNull { runCatching { JSONObject(it as String) }.getOrNull() }.sortedBy { -it.getLong("id") }
    }
    fun record(id: Long): JSONObject? = synchronized(lock) { prefs.getString(id.toString(), null)?.let(::JSONObject) }
    fun importPending(old: JSONObject): Long = synchronized(lock) {
        records().firstOrNull { it.getString("owner") == old.getString("owner") && it.getString("episode") == old.getString("episode") && it.optString("state") != "failed" }?.getLong("id") ?: run {
            val id = -maxOf(System.currentTimeMillis(), prefs.getLong("sequence", 0) + 1)
            val data = JSONObject(old.toString()).put("id", id).put("state", "queued").put("metered", false)
                .put("bytes", 0).put("total", -1).put("percent", -1).put("message", "Reiniciada en la cola de fuentes automáticas")
            data.remove("file")
            check(prefs.edit().putLong("sequence", -id).putString(id.toString(), data.toString()).commit())
            id
        }
    }
    fun update(id: Long, change: (JSONObject) -> Unit): Boolean = synchronized(lock) {
        val data = record(id) ?: return false
        change(data)
        prefs.edit().putString(id.toString(), data.toString()).commit()
    }
    fun list(): List<OfflineEpisode> {
        val owner = owner() ?: return emptyList()
        return records().filter { it.getString("owner") == owner }.map { data ->
            val id = data.getLong("id")
            val complete = data.optString("state") == "complete" && OfflineCacheFiles.available(context, id)
            val status = when {
                complete -> DownloadManager.STATUS_SUCCESSFUL
                data.optString("state") in setOf("failed", "complete") -> DownloadManager.STATUS_FAILED
                data.optString("state") == "running" -> DownloadManager.STATUS_RUNNING
                else -> DownloadManager.STATUS_PENDING
            }
            OfflineEpisode(id, data.getString("episode"), data.getString("title"), data.getString("server"), status,
                data.optLong("bytes"), data.optLong("total", -1), 0, data.optString("serverName"), if(java.io.File(OfflineCacheFiles.directory(context, id), "cache").isDirectory) data.optLong("bytes") else 0,
                data.optDouble("percent", -1.0).toFloat(), data.optString("message").ifEmpty { null })
        }
    }
    fun remove(id: Long) = synchronized(lock) {
        val data = record(id) ?: return@synchronized
        if(data.getString("owner") != owner()) return@synchronized
        check(prefs.edit().remove(id.toString()).commit())
        OfflineCacheFiles.erase(context, id)
    }
    fun source(id: Long): CachedPlayback? = if(list().any { it.id == id && it.complete }) OfflineCacheFiles.source(context, id) else null
    fun lastProvider(): String? = prefs.getString("lastProvider", null)
    fun lastProvider(value: String) { prefs.edit().putString("lastProvider", value).commit() }
    fun cooling(now: Long): Set<String> = DownloadOriginPolicy.providers.filter { prefs.getLong("cooldown:$it", 0) > now }.toSet()
    fun cooldown(provider: String, until: Long) { prefs.edit().putLong("cooldown:$provider", until).commit() }
    companion object {
        private val lock = Any()
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_ROAMING).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            // Each pump drains the persistent queue. Cancelling one chapter cannot cancel later chapters.
            WorkManager.getInstance(context).enqueueUniqueWork("animeflix-offline-serial", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        fun waitForUnmetered(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("animeflix-offline-unmetered", ExistingWorkPolicy.KEEP, request)
        }
    }
}
