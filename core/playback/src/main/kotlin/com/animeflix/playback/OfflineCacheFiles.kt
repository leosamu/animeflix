package com.animeflix.playback

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.media3.common.StreamKey
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.animeflix.data.ResolvedVideo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class CachedPlayback(val video: ResolvedVideo, val keys: List<StreamKey>, val cacheId: Long)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object OfflineCacheFiles {
    private data class Lease(val cache: SimpleCache, var users: Int = 1)
    private val leases = mutableMapOf<Long, Lease>()
    private val deleting = mutableSetOf<Long>()
    private var database: StandaloneDatabaseProvider? = null
    fun directory(context: Context, id: Long): File {
        require(id < 0 && id != Long.MIN_VALUE)
        return File(context.noBackupFilesDir, "offline-v2/${-id}")
    }
    @Synchronized fun acquire(context: Context, id: Long): SimpleCache {
        leases[id]?.let { it.users++; return it.cache }
        val provider = database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }
        return SimpleCache(File(directory(context, id), "cache"), NoOpCacheEvictor(), provider).also { leases[id] = Lease(it) }
    }
    @Synchronized fun release(context: Context, id: Long) {
        val lease = leases[id] ?: return
        if(--lease.users == 0) {
            lease.cache.release(); leases.remove(id)
            if(deleting.remove(id)) erase(context, id)
        }
    }
    @Synchronized fun erase(context: Context, id: Long) {
        if(leases.containsKey(id)) { deleting.add(id); return }
        val target = directory(context, id).canonicalFile
        check(target.parentFile == File(context.noBackupFilesDir, "offline-v2").canonicalFile)
        if(target.exists()) check(target.deleteRecursively()) { "No se pudo liberar el archivo descargado." }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("animeflix.offline", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("animeflix.offline", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun saveSource(context: Context, id: Long, video: ResolvedVideo, keys: List<StreamKey>) {
        val data = JSONObject().put("uri", video.url).put("mime", video.mimeType ?: "video/mp4")
            .put("keys", JSONArray(keys.map { JSONArray(listOf(it.groupIndex, it.streamIndex)) }))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val file = AtomicFile(File(directory(context, id), "source.bin"))
        val out = file.startWrite()
        try { out.write(cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))); file.finishWrite(out) }
        catch(failure: Exception) { file.failWrite(out); throw failure }
    }
    @Synchronized fun source(context: Context, id: Long): CachedPlayback? = runCatching {
        val bytes = AtomicFile(File(directory(context, id), "source.bin")).readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))) }
        val data = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val keys = data.getJSONArray("keys").let { array -> (0 until array.length()).map { array.getJSONArray(it).let { key -> StreamKey(key.getInt(0), key.getInt(1)) } } }
        CachedPlayback(ResolvedVideo(data.getString("uri"), "", mimeType = data.getString("mime")), keys, id)
    }.getOrNull()
    fun available(context: Context, id: Long) = File(directory(context, id), "source.bin").isFile && File(directory(context, id), "cache").isDirectory
}
