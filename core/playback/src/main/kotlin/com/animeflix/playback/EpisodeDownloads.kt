package com.animeflix.playback

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.animeflix.data.AccountManager
import com.animeflix.data.AnimeAv1Episodes
import com.animeflix.data.Mp4UploadResolver
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class OfflineEpisode(val id: Long, val episodeUrl: String, val title: String, val serverUrl: String,
    val status: Int, val bytes: Long, val total: Long, val reason: Int, val serverName: String = "MP4Upload", val storedBytes: Long = 0, val percent: Float = -1f, val message: String? = null) {
    val complete get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED || status == 0
    val active get() = !complete && !failed
}

/** Reads 0.10.0 files and the new persistent serial queue; completed legacy files remain playable. */
class EpisodeDownloads(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val index = this.context.getSharedPreferences("offline_episodes_v1", Context.MODE_PRIVATE)
    private fun owner(): String? = AccountManager.create(context).state.value.let {
        if(it.accountId == null || it.selected == null) null else "${it.accountId}:${it.selected?.id}"
    }

    suspend fun enqueue(episodeUrl: String, title: String, server: PlaybackServer, metered: Boolean = false): Long {
        require(AnimeAv1Episodes.isEpisodeUrl(episodeUrl) && com.animeflix.data.DownloadOriginPolicy.provider(server) != null)
        withContext(Dispatchers.IO) { list().firstOrNull { it.episodeUrl == episodeUrl && !it.failed } }?.let { return it.id }
        return OfflineQueueStore(context).enqueue(episodeUrl, title, server, metered)
    }

    internal fun cachedPlayback(id: Long) = OfflineQueueStore(context).source(id)
    fun resumeQueue() = OfflineQueueStore.kick(context)

    internal fun migrateActiveLegacy() = synchronized(lock) {
        val queue = OfflineQueueStore(context)
        index.all.forEach { (key, raw) ->
            val id = key.toLongOrNull() ?: return@forEach
            val data = runCatching { JSONObject(raw as String) }.getOrNull() ?: return@forEach
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if(cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)) {
                    queue.importPending(data) // Durable metadata first; a crash can safely repeat the import.
                    manager.remove(id); file(data).delete()
                    index.edit().remove(key).commit()
                }
            }
        }
    }

    fun list(): List<OfflineEpisode> {
        val owner = owner() ?: return emptyList()
        val legacy = index.all.mapNotNull { (key, value) -> runCatching {
            val json = JSONObject(value as String)
            if(json.getString("owner") != owner) return@runCatching null
            val id = key.toLong()
            var status = 0; var bytes = 0L; var total = -1L; var reason = 0
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if(cursor.moveToFirst()) {
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
            }
            if(status == DownloadManager.STATUS_SUCCESSFUL) {
                val file = file(json)
                val valid = file.isFile && file.length() == bytes && bytes > 12 && (total <= 0 || bytes == total) &&
                    file.inputStream().use { input ->
                        val header = ByteArray(12)
                        input.read(header) == 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp"
                    }
                if(!valid) status = 0
            }
            OfflineEpisode(id, json.getString("episode"), json.getString("title"), json.getString("server"), status, bytes, total, reason, json.optString("serverName", "MP4Upload"), file(json).length())
        }.getOrNull() }
        return (legacy + OfflineQueueStore(context).list()).sortedByDescending { if(it.id < 0) -it.id else it.id }
    }

    fun remove(id: Long) = synchronized(lock) {
        if(id < 0) { OfflineQueueStore(context).remove(id); return@synchronized }
        if(list().none { it.id == id }) return@synchronized
        val json = JSONObject(checkNotNull(index.getString(id.toString(), null)))
        manager.remove(id)
        file(json).delete()
        index.edit().remove(id.toString()).commit()
        Unit
    }

    fun localUri(id: Long): Uri? {
        if(id < 0) return cachedPlayback(id)?.video?.url?.let(Uri::parse)
        if(list().none { it.id == id && it.complete }) return null
        val json = JSONObject(index.getString(id.toString(), null) ?: return null)
        return Uri.fromFile(file(json))
    }

    private fun file(json: JSONObject): File {
        val name = json.getString("file")
        require(Regex("animeflix-[a-f0-9-]+\\.mp4").matches(name))
        return File(checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)), name)
    }
    companion object { private val lock = Any() }
}

fun offlineSize(bytes: Long): String = if(bytes < 0) "Tamaño pendiente" else "%.1f MB".format(bytes / 1_000_000.0)

fun OfflineEpisode.statusLabel(): String = when {
    complete -> "Disponible sin conexión · ${offlineSize(bytes)}"
    message != null -> message
    reason == DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Espacio insuficiente. Libera almacenamiento y reintenta."
    failed -> "Descarga incompleta o enlace caducado. Reintenta con un enlace nuevo."
    status == DownloadManager.STATUS_PAUSED -> "En espera de conexión o de una red permitida"
    status == DownloadManager.STATUS_PENDING -> "En cola"
    else -> "${offlineSize(bytes)} / ${offlineSize(total)}"
}
