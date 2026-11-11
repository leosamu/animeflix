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
    val status: Int, val bytes: Long, val total: Long, val reason: Int, val serverName: String = "MP4Upload", val storedBytes: Long = 0) {
    val complete get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED || status == 0
    val active get() = !complete && !failed
}

/** Android owns the persistent transfer queue. Our index contains identities, never media URLs or cookies.
 * Only progressive MP4Upload is enabled: a retry resolves afresh and starts a new file, never splices URLs.
 */
class EpisodeDownloads(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val index = this.context.getSharedPreferences("offline_episodes_v1", Context.MODE_PRIVATE)
    private fun owner(): String? = AccountManager.create(context).state.value.let {
        if(it.accountId == null || it.selected == null) null else "${it.accountId}:${it.selected?.id}"
    }

    suspend fun enqueue(episodeUrl: String, title: String, server: PlaybackServer, metered: Boolean = false): Long {
        require(AnimeAv1Episodes.isEpisodeUrl(episodeUrl) && Mp4UploadResolver.supports(server.embedUrl))
        val owner = checkNotNull(owner()) { "Selecciona un perfil antes de descargar." }
        withContext(Dispatchers.IO) { list().firstOrNull { it.episodeUrl == episodeUrl && !it.failed } }?.let { return it.id }
        val video = Mp4UploadResolver().resolve(server)
        return withContext(Dispatchers.IO) {
            check(owner() == owner) { "El perfil ha cambiado. Vuelve a intentarlo." }
            val root = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)) { "Almacenamiento no disponible." }
            check(root.usableSpace > 256L * 1024 * 1024) { "Libera al menos 256 MB antes de descargar." }
            synchronized(lock) {
                check(owner() == owner) { "El perfil ha cambiado. Vuelve a intentarlo." }
                // Also guards two rapid clicks while the source was resolving.
                list().firstOrNull { it.episodeUrl == episodeUrl && !it.failed }?.id ?: run {
                    val name = "animeflix-${UUID.randomUUID()}.mp4"
                    val request = DownloadManager.Request(Uri.parse(video.url))
                        .setTitle(title.take(160)).setDescription("AnimeFlix · vídeo sin conexión")
                        .setMimeType("video/mp4")
                        .addRequestHeader("Referer", video.referer).addRequestHeader("User-Agent", video.userAgent)
                        .setAllowedOverMetered(metered).setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, name)
                    val id = manager.enqueue(request)
                    val saved = JSONObject().put("owner", owner).put("episode", episodeUrl)
                        .put("title", title.take(160)).put("server", server.embedUrl).put("serverName", server.name.take(100)).put("file", name)
                    if(!index.edit().putString(id.toString(), saved.toString()).commit()) {
                        manager.remove(id); error("No se pudo guardar la descarga.")
                    }
                    id
                }
            }
        }
    }

    fun list(): List<OfflineEpisode> {
        val owner = owner() ?: return emptyList()
        return index.all.mapNotNull { (key, value) -> runCatching {
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
        }.getOrNull() }.sortedByDescending { it.id }
    }

    fun remove(id: Long) = synchronized(lock) {
        if(list().none { it.id == id }) return@synchronized
        val json = JSONObject(checkNotNull(index.getString(id.toString(), null)))
        manager.remove(id)
        file(json).delete()
        index.edit().remove(id.toString()).commit()
        Unit
    }

    fun localUri(id: Long): Uri? {
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
    reason == DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Espacio insuficiente. Libera almacenamiento y reintenta."
    failed -> "Descarga incompleta o enlace caducado. Reintenta con un enlace nuevo."
    status == DownloadManager.STATUS_PAUSED -> "En espera de conexión o de una red permitida"
    status == DownloadManager.STATUS_PENDING -> "En cola"
    else -> "${offlineSize(bytes)} / ${offlineSize(total)}"
}
