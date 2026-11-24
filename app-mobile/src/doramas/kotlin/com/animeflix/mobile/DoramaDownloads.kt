package com.animeflix.mobile

import android.app.DownloadManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.animeflix.data.DoramaEpisode
import com.animeflix.data.DoramasCatalog
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

internal data class DoramaDownloadOffer(val url: Uri, val mime: String, val userAgent: String) {
    companion object {
        fun from(url: String, mime: String?, disposition: String?, userAgent: String): DoramaDownloadOffer? {
            val uri = Uri.parse(url)
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
            val contentType = mime?.substringBefore(';')?.lowercase()
            val mp4 = uri.lastPathSegment?.endsWith(".mp4", true) == true ||
                disposition?.contains(Regex("\\.mp4(?:[\"';]|$)", RegexOption.IGNORE_CASE)) == true
            if (contentType != "video/mp4" && !(mp4 && contentType in listOf(null, "application/octet-stream"))) return null
            return DoramaDownloadOffer(uri, "video/mp4", userAgent)
        }
    }
}

internal class DoramaDownloads(private val context: Context, profile: String) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val profileDirectory = profile.toByteArray().let { bytes ->
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private val prefs = context.getSharedPreferences("dorama_downloads_$profileDirectory", Context.MODE_PRIVATE)

    data class Entry(
        val id: Long, val episode: DoramaEpisode, val title: String, val status: Int,
        val bytes: Long, val total: Long, val localUri: Uri?
    ) {
        val description get() = when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> "Disponible sin conexión"
            DownloadManager.STATUS_FAILED -> "No se pudo descargar"
            DownloadManager.STATUS_RUNNING -> if (total > 0) "Descargando · ${bytes * 100 / total}%" else "Descargando"
            DownloadManager.STATUS_PAUSED -> "En pausa"
            else -> "En cola"
        }
    }

    fun enqueue(offer: DoramaDownloadOffer, episode: DoramaEpisode, title: String) {
        val path = "$profileDirectory/${episode.slug}-${episode.season}x${episode.number}.mp4"
        val existing = entries().find { it.episode == episode }
        if (existing != null && existing.status != DownloadManager.STATUS_FAILED) return
        existing?.let { remove(it.id) }
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), profileDirectory).mkdirs()
        val request = DownloadManager.Request(offer.url).setMimeType(offer.mime)
            .setTitle("$title · T${episode.season} E${episode.number}")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, path)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (offer.userAgent.isNotBlank()) request.addRequestHeader("User-Agent", offer.userAgent)
        val id = manager.enqueue(request)
        prefs.edit().putString(id.toString(), JSONObject().put("episode", episode.url).put("title", title).toString()).apply()
    }

    fun entries(): List<Entry> = prefs.all.mapNotNull { (key, value) ->
        val id = key.toLongOrNull() ?: return@mapNotNull null
        val metadata = runCatching { JSONObject(value as String) }.getOrNull() ?: return@mapNotNull null
        val episode = DoramasCatalog.episode(metadata.optString("episode")) ?: return@mapNotNull null
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return@use null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            Entry(id, episode, metadata.optString("title"), status,
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                if (status == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id) else null)
        }
    }.sortedByDescending { it.id }

    fun remove(id: Long) {
        manager.remove(id)
        prefs.edit().remove(id.toString()).apply()
    }
}

@Composable
internal fun DoramaDownloadsList(downloads: DoramaDownloads, onOpen: (DoramaDownloads.Entry) -> Unit) {
    var entries by remember { mutableStateOf(downloads.entries()) }
    LaunchedEffect(downloads) {
        while (true) {
            entries = downloads.entries()
            delay(1_000)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) item {
            Text("Las descargas MP4 que ofrezca un servidor aparecerán aquí.", Modifier.padding(16.dp))
        }
        items(entries, key = { it.id }) { entry ->
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = { Text("T${entry.episode.season} · E${entry.episode.number} · ${entry.description}") },
                trailingContent = {
                    Row {
                        if (entry.localUri != null) TextButton(onClick = { onOpen(entry) }) { Text("Ver") }
                        TextButton(onClick = { downloads.remove(entry.id); entries = downloads.entries() }) {
                            Text(if (entry.localUri != null) "Eliminar" else "Quitar")
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun DoramaOfflinePlayer(entry: DoramaDownloads.Entry, resumeMs: Long,
    onProgress: (Long, Long) -> Unit, onBack: () -> Unit) {
    var video by remember { mutableStateOf<VideoView?>(null) }
    var error by remember { mutableStateOf(false) }
    val report by rememberUpdatedState(onProgress)
    BackHandler(onBack = onBack)
    LaunchedEffect(video) {
        while (true) {
            delay(5_000)
            video?.takeIf { it.isPlaying && it.duration > 0 }?.let {
                report(it.currentPosition.toLong(), it.duration.toLong())
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Volver") }
        Text(entry.title, Modifier.padding(horizontal = 16.dp))
        if (error) Text("No se pudo reproducir este archivo.", Modifier.padding(16.dp))
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnPreparedListener {
                    if (resumeMs in 1 until duration.toLong()) seekTo(resumeMs.toInt())
                    start()
                }
                setOnCompletionListener { player: MediaPlayer -> report(player.duration.toLong(), player.duration.toLong()) }
                setOnErrorListener { _, _, _ -> error = true; true }
                setVideoURI(entry.localUri)
                video = this
            }
        }, onRelease = { player ->
            if (player.duration > 0 && player.currentPosition > 0) {
                report(player.currentPosition.toLong(), player.duration.toLong())
            }
            player.stopPlayback()
        })
    }
}
