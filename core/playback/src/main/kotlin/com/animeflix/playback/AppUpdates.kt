package com.animeflix.playback

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

internal data class UpdateStatus(val message: String = "", val busy: Boolean = false, val ready: Boolean = false)

/** Manual checks include preview releases; the installer always requires Android's consent. */
internal class AppUpdates private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val mutable = MutableStateFlow(UpdateStatus())
    val state = mutable.asStateFlow()
    private val apk get() = File(context.getExternalFilesDir(null), "updates/update.apk")
    init {
        if (prefs.contains("download")) {
            mutable.value = UpdateStatus("Comprobando descarga pendiente…", busy = true)
            scope.launch {
                val installed = context.packageManager.getPackageInfo(context.packageName, 0)
                val code = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed)
                val expected = runCatching { JSONObject(prefs.getString("artifact", "")!!).getLong("versionCode") }.getOrDefault(Long.MAX_VALUE)
                if (code >= expected) {
                    manager.remove(prefs.getLong("download", -1))
                    prefs.edit().clear().apply()
                    mutable.value = UpdateStatus("Versión ${installed.versionName} instalada.")
                } else monitor()
            }
        }
    }

    fun check() {
        if (mutable.value.busy) return
        mutable.value = UpdateStatus("Buscando actualización…", busy = true)
        scope.launch {
            try {
                if (context.packageName !in listOf("com.animeflix.mobile", "com.animeflix.tv")) {
                    mutable.value = UpdateStatus("Las actualizaciones están disponibles en la versión instalada desde GitHub.")
                    return@launch
                }
                val releases = JSONArray(read("https://api.github.com/repos/leosamu/animeflix/releases?per_page=20"))
                val installed = context.packageManager.getPackageInfo(context.packageName, 0)
                val current = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed)
                var best: JSONObject? = null
                var bestUrl: String? = null
                var bestCode = current
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("draft")) continue
                    val assets = release.getJSONArray("assets")
                    val manifestUrl = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "release-manifest.json" }?.optString("browser_download_url") ?: continue
                    val manifest = JSONObject(read(manifestUrl))
                    val compatible = com.animeflix.data.compatibleAppRelease(manifest, context.packageName, bestCode) ?: continue
                    val artifacts = manifest.getJSONArray("artifacts")
                    for (j in 0 until artifacts.length()) {
                        val artifact = artifacts.getJSONObject(j)
                        if (artifact.optString("applicationId") != context.packageName || artifact.optLong("versionCode") != compatible.versionCode ||
                            artifact.optString("file") != compatible.file || artifact.optString("sha256") != compatible.sha256) continue
                        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                            .firstOrNull { it.optString("name") == artifact.optString("file") } ?: continue
                        require(artifact.getString("sha256").matches(Regex("[a-fA-F0-9]{64}")))
                        best = artifact; bestCode = artifact.getLong("versionCode"); bestUrl = asset.getString("browser_download_url")
                    }
                }
                if (best == null) { mutable.value = UpdateStatus("Ya tienes la versión más reciente (${installed.versionName})."); return@launch }
                val artifact = best
                requireGithub(bestUrl!!)
                apk.parentFile!!.mkdirs()
                apk.delete()
                val id = manager.enqueue(DownloadManager.Request(Uri.parse(bestUrl))
                    .setTitle("AnimeFlix ${artifact.getString("versionName")}")
                    .setDescription("Actualización de la aplicación")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(apk)))
                prefs.edit().putString("artifact", artifact.toString()).putLong("download", id).apply()
                monitor()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = UpdateStatus("No se pudo buscar o descargar la actualización. Comprueba la conexión y vuelve a intentarlo.")
            }
        }
    }

    private suspend fun monitor() {
        try {
            val id = prefs.getLong("download", -1)
            while (true) {
                val status = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    check(cursor.moveToFirst())
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    mutable.value = UpdateStatus(if (total > 0) "Descargando actualización · ${done * 100 / total}%" else "Esperando descarga de la actualización…", busy = true)
                    status
                }
                check(status != DownloadManager.STATUS_FAILED)
                if (status == DownloadManager.STATUS_SUCCESSFUL) break
                delay(1000)
            }
            verify()
            mutable.value = UpdateStatus("Actualización lista. Pulsa Instalar y confirma en Android.", ready = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            prefs.edit().remove("download").apply()
            mutable.value = UpdateStatus("La actualización no pudo descargarse o verificarse. Vuelve a buscarla.")
        }
    }

    @Suppress("DEPRECATION")
    private fun verify() {
        val artifact = JSONObject(prefs.getString("artifact", null) ?: error("No manifest"))
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        check(digest.digest().joinToString("") { "%02x".format(it) }.equals(artifact.getString("sha256"), true))
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        check(archive.packageName == context.packageName)
        check((archive.applicationInfo?.flags ?: android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0)
        check(androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(archive) == artifact.getLong("versionCode"))
        check(androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(archive) > androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed))
        check(archive.signatures?.isNotEmpty() == true && archive.signatures!!.toSet() == installed.signatures!!.toSet())
    }

    fun install() {
        if (mutable.value.busy) return
        mutable.value = UpdateStatus("Verificando actualización…", busy = true)
        scope.launch {
            try {
                verify()
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        mutable.value = UpdateStatus("Autoriza esta aplicación en Android, vuelve y pulsa Instalar.", ready = true)
                    } else {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        mutable.value = UpdateStatus("Confirma la instalación en Android. Si la cancelas, puedes volver a intentarlo.", ready = true)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = UpdateStatus("No se pudo abrir el instalador. Vuelve a buscar la actualización.")
            }
        }
    }

    private fun requireGithub(value: String) {
        val uri = Uri.parse(value)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.path?.startsWith("/leosamu/animeflix/releases/download/") == true)
    }
    private fun read(value: String): String {
        if (!value.startsWith("https://api.github.com/repos/leosamu/animeflix/releases?")) requireGithub(value)
        val connection = URL(value).openConnection().apply { connectTimeout = 15_000; readTimeout = 15_000; setRequestProperty("User-Agent", "AnimeFlix-Android") }
        return connection.getInputStream().use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; check(output.size() + n <= 2_000_000); output.write(buffer, 0, n) }
            output.toString("UTF-8")
        }
    }
    companion object {
        @Volatile private var instance: AppUpdates? = null
        fun get(context: Context): AppUpdates = instance ?: synchronized(this) { instance ?: AppUpdates(context.applicationContext).also { instance = it } }
    }
}
