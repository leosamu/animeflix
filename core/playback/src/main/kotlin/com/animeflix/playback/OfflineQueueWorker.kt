package com.animeflix.playback

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.hls.playlist.*
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.exoplayer.upstream.ParsingLoadable
import androidx.work.*
import com.animeflix.data.*
import com.animeflix.model.PlaybackServer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.Executor

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineQueueWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val store = OfflineQueueStore(context)
    private val context = context.applicationContext
    override suspend fun doWork(): Result = serial.withLock {
        withContext(Dispatchers.IO) { EpisodeDownloads(context).migrateActiveLegacy() }
        if(store.records().none { it.optString("state") in setOf("queued", "running") }) return@withLock Result.success()
        @Suppress("DEPRECATION")
        val roaming = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isRoaming == true
        if(roaming) return@withLock Result.retry()
        setForeground(notification("Preparando descargas", "Una descarga cada vez"))
        try {
            while(true) {
                val pending = store.records().filter { it.optString("state") in setOf("queued", "running") }
                if(pending.isEmpty()) return@withLock Result.success()
                val metered = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
                val eligible = pending.filter { it.optLong("retryAfter", 0) <= System.currentTimeMillis() }
                val item = eligible.firstOrNull { !metered || it.optBoolean("metered") } ?: run {
                    if(metered) eligible.forEach { job -> store.update(job.getLong("id")) { it.put("message", "Esperando una red sin coste medido") } }
                    if(metered && eligible.isNotEmpty()) { OfflineQueueStore.waitForUnmetered(context); return@withLock Result.success() }
                    return@withLock Result.retry()
                }
                val id = item.getLong("id")
                try {
                    coroutineScope {
                        val transfer = async { download(item) }
                        val cancelled = launch {
                            while(transfer.isActive) {
                                delay(500)
                                if(store.record(id) == null) { transfer.cancel(); break }
                                if(!item.optBoolean("metered") && (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered) {
                                    transfer.cancel(CancellationException("Waiting for unmetered network")); break
                                }
                            }
                        }
                        try { transfer.await() } finally { cancelled.cancel() }
                    }
                } catch(cancelled: CancellationException) {
                    if(!currentCoroutineContext().isActive) {
                        withContext(NonCancellable + Dispatchers.IO) { store.update(id) { it.put("state", "queued").put("message", "Esperando para continuar con un enlace nuevo") } }
                        throw cancelled
                    }
                    // Only this chapter was removed. Later chapters remain queued.
                    if(store.record(id) != null) {
                        store.update(id) { it.put("state", "queued").put("message", "Esperando una red permitida") }
                        if((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered) {
                            OfflineQueueStore.waitForUnmetered(context); return@withLock Result.success()
                        }
                        return@withLock Result.retry()
                    }
                } catch(failure: Exception) {
                    if((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork == null) {
                        store.update(id) { it.put("state", "queued").put("message", "Esperando conexión") }
                        return@withLock Result.retry()
                    }
                    store.update(id) { it.put("state", "failed").put("message", "No se pudo completar con las fuentes disponibles. Puedes reintentar.") }
                } finally {
                    if(store.record(id) == null) withContext(NonCancellable + Dispatchers.IO) { OfflineCacheFiles.erase(context, id) }
                }
            }
        } finally { /* WorkManager owns foreground-service cleanup, including system cancellation. */ }
        @Suppress("UNREACHABLE_CODE") Result.success()
    }

    private suspend fun download(item: JSONObject) {
        val id = item.getLong("id")
        val episode = item.getString("episode")
        val title = item.getString("title")
        store.update(id) { it.put("state", "running").put("message", "Consultando fuentes del episodio"); it.remove("retryAfter") }
        val servers = AnimeAv1Episodes().servers(episode)
        val preferred = PlaybackServer(item.getString("serverName"), item.getString("server"))
        val candidates = DownloadOriginPolicy.candidates(servers, preferred, store.lastProvider(), store.cooling(System.currentTimeMillis()))
        if(candidates.isEmpty()) {
            if(DownloadOriginPolicy.candidates(servers, preferred, store.lastProvider()).isNotEmpty()) {
                store.update(id) { it.put("state", "queued").put("retryAfter", System.currentTimeMillis() + 30_000).put("message", "Fuentes temporalmente ocupadas; esperando para continuar") }
                return
            }
            store.update(id) { it.put("state", "failed").put("message", "No hay fuentes disponibles en el idioma elegido. Espera un momento y reintenta.") }
            return
        }
        for((candidateIndex, server) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            val provider = checkNotNull(DownloadOriginPolicy.provider(server))
            if(store.record(id) == null) throw CancellationException("Removed")
            store.lastProvider(provider)
            store.update(id) { it.put("serverName", server.name).put("server", server.embedUrl).put("message", "Preparando $provider").put("bytes", 0).put("total", -1).put("percent", -1) }
            try {
                setForeground(notification(title, "Preparando $provider"))
                val video = DownloadSourceResolver(context).resolve(server, episode)
                transfer(id, title, provider, video, candidateIndex < candidates.lastIndex)
                store.update(id) { it.put("state", "complete").put("message", "").put("percent", 100) }
                return
            } catch(failure: Exception) {
                if(failure is CancellationException) throw failure
                OfflineCacheFiles.erase(context, id)
                if(context.noBackupFilesDir.usableSpace < 256L * 1024 * 1024) {
                    store.update(id) { it.put("state", "failed").put("message", "Espacio insuficiente. Libera almacenamiento antes de reintentar.") }
                    return
                }
                if((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork == null) throw failure
                val code = generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
                store.cooldown(provider, System.currentTimeMillis() + if(code in setOf(403, 429, 503)) 10 * 60_000 else 60_000)
                store.update(id) { it.put("message", "$provider no disponible; probando otro origen") }
                android.util.Log.i("AnimeFlixOfflineQueue", "$provider unavailable; HTTP=$code; ${failure.javaClass.simpleName}")
            }
        }
        store.update(id) { it.put("state", "failed").put("message", "Los orígenes compatibles no han completado el episodio. Reintenta más tarde.") }
    }

    private suspend fun transfer(id: Long, title: String, provider: String, video: ResolvedVideo, canRotate: Boolean) = withContext(Dispatchers.IO) {
        OfflineCacheFiles.erase(context, id) // Never combine bytes from different origins or refreshed links.
        check(context.noBackupFilesDir.usableSpace > 256L * 1024 * 1024) { "Espacio insuficiente" }
        val headers = buildMap {
            put("Referer", video.referer)
            video.origin?.let { put("Origin", it) }
        }
        val http = DefaultHttpDataSource.Factory().setUserAgent(video.userAgent).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val upstream = ResolvingDataSource.Factory(http) { spec ->
            val uri = spec.uri
            check(uri.scheme == "https" && uri.host != null) { "Fuente de descarga no compatible" }
            spec.withAdditionalHeaders(headers + if(uri.host == Uri.parse(video.url).host && video.cookie != null) mapOf("Cookie" to video.cookie.orEmpty()) else emptyMap())
        }
        val hls = video.mimeType?.contains("mpegurl", true) == true || Uri.parse(video.url).path.orEmpty().endsWith(".m3u8")
        val keys = if(hls) selectTracks(upstream, video.url) else emptyList()
        val cache = OfflineCacheFiles.acquire(context, id)
        try {
            val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            val media = MediaItem.Builder().setUri(video.url).setMimeType(if(hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4).setStreamKeys(keys).build()
            val executor = Executor { it.run() }
            val downloader: Downloader = if(hls) HlsDownloader(media, factory, executor) else ProgressiveDownloader(media, factory, executor)
            var last = 0L
            val started = android.os.SystemClock.elapsedRealtime()
            store.update(id) { it.put("message", "Descargando desde $provider") }
            setForeground(notification(title, "Descargando desde $provider"))
            try {
                runInterruptible {
                    downloader.download { total, bytes, percent ->
                        if(Thread.currentThread().isInterrupted) throw InterruptedException()
                        val now = android.os.SystemClock.elapsedRealtime()
                        val elapsed = now - started
                        // Try another available origin once, without making a slow network fail on the last source.
                        if(DownloadOriginPolicy.shouldRotateSlowSource(elapsed, total, bytes, percent, canRotate)) throw IllegalStateException("El origen es demasiado lento; hay alternativas")
                        if(now - last >= 1_000 || percent == 100f) {
                            last = now
                            store.update(id) { it.put("bytes", bytes).put("total", total).put("percent", if(percent.isFinite()) percent.toDouble() else -1.0) }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if(!hls) {
                    val local = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null).createDataSource()
                    try {
                        local.open(DataSpec.Builder().setUri(video.url).setLength(12).build())
                        val header = ByteArray(12)
                        var read = 0
                        while(read < header.size) { val count = local.read(header, read, header.size - read); check(count > 0); read += count }
                        check(String(header, 4, 4, Charsets.US_ASCII) == "ftyp") { "El origen no entregó un MP4" }
                    } finally { local.close() }
                }
                OfflineCacheFiles.saveSource(context, id, video.copy(mimeType = if(hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4), keys)
                store.update(id) { it.put("bytes", cache.cacheSpace) }
            } finally { downloader.cancel() }
        } finally { OfflineCacheFiles.release(context, id) }
    }

    private fun selectTracks(factory: DataSource.Factory, url: String): List<StreamKey> {
        val playlist = ParsingLoadable.load(factory.createDataSource(), HlsPlaylistParser(), Uri.parse(url), C.DATA_TYPE_MANIFEST)
        fun validate(media: HlsMediaPlaylist) {
            check(media.protectionSchemes == null) { "La fuente requiere licencia offline" }
            check(media.hasEndTag && media.durationUs > 60_000_000) { "La fuente no publica un episodio completo bajo demanda" }
        }
        if(playlist is HlsMediaPlaylist) { validate(playlist); return emptyList() }
        val master = playlist as HlsMultivariantPlaylist
        check(master.sessionKeyDrmInitData.isEmpty()) { "La fuente requiere licencia offline" }
        val variant = master.variants.withIndex().filter { it.value.format.height in 1..720 }.maxByOrNull { it.value.format.height }
            ?: master.variants.withIndex().minByOrNull { it.value.format.bitrate.coerceAtLeast(0) } ?: error("No hay pista de vídeo")
        val keys = mutableListOf(StreamKey(0, variant.index))
        validate(ParsingLoadable.load(factory.createDataSource(), HlsPlaylistParser(), variant.value.url, C.DATA_TYPE_MANIFEST) as HlsMediaPlaylist)
        val audio = master.audios.withIndex().filter { it.value.groupId == variant.value.audioGroupId }
        (audio.firstOrNull { it.value.format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 } ?: audio.firstOrNull())?.let {
            keys += StreamKey(1, it.index)
            it.value.url?.let { uri -> validate(ParsingLoadable.load(factory.createDataSource(), HlsPlaylistParser(), uri, C.DATA_TYPE_MANIFEST) as HlsMediaPlaylist) }
        }
        master.subtitles.withIndex().firstOrNull { it.value.groupId == variant.value.subtitleGroupId }?.let { keys += StreamKey(2, it.index) }
        return keys
    }

    private fun notification(title: String, detail: String): ForegroundInfo {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("offline-downloads", "Descargas de episodios", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(context, 0, Intent(context, DownloadLibraryActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "offline-downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(detail).setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).build()
        return if(Build.VERSION.SDK_INT >= 29) ForegroundInfo(62100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(62100, notification)
    }
    companion object { private val serial = Mutex() }
}
