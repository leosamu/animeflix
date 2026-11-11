package com.animeflix.mobile

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import com.animeflix.playback.PlayerActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.concurrent.Executor

/** Small official AndroidX sample: tests the real progressive downloader/cache/player without provider throttling.
 * Reflection accesses our internal cache in QA only; no fixture API is added to release builds.
 */
@RunWith(AndroidJUnit4::class)
class OfflineMp4CacheTest {
    @get:Rule val rule = createEmptyComposeRule()
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun mp4CacheWithHttpsIdentityPlaysWithoutNetwork(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveOffline") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim() }
        val wifi = shell("settings get global wifi_on")
        val mobile = shell("settings get global mobile_data")
        val accounts = AccountManager.create(context)
        val sample = File(context.cacheDir, "androidx-offline-sample.mp4")
        val index = context.getSharedPreferences("offline_queue_v2", Context.MODE_PRIVATE)
        val id = -1_000_000L
        val methods = Class.forName("com.animeflix.playback.OfflineCacheFiles")
        val pool = methods.getField("INSTANCE").get(null)
        val source = "https://offline-fixture.invalid/sample.mp4"
        var acquired = false
        try {
            shell("am start -W -n ${context.packageName}/com.animeflix.mobile.UiReviewActivity --es screen offline-test")
            val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            check(network.activeNetwork != null && !network.isActiveNetworkMetered)
            accounts.beginPreview(); accounts.select(accounts.state.value.profiles.first())
            val connection = URL("https://raw.githubusercontent.com/androidx/media/release/libraries/test_data/src/test/assets/media/mp4/sample.mp4").openConnection().apply { connectTimeout = 20_000; readTimeout = 20_000 }
            connection.getInputStream().use { input -> sample.outputStream().use { output ->
                val buffer = ByteArray(16_384); var total = 0
                while(true) { val count = input.read(buffer); if(count < 0) break; total += count; check(total <= 2_000_000); output.write(buffer, 0, count) }
            } }
            val cache = methods.getMethod("acquire", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id) as SimpleCache
            acquired = true
            val local = ResolvingDataSource.Factory(FileDataSource.Factory()) { spec -> spec.withUri(Uri.fromFile(sample)) }
            val factory = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(local)
            ProgressiveDownloader(MediaItem.fromUri(source), factory, Executor { it.run() }).download(null)
            methods.getMethod("saveSource", Context::class.java, java.lang.Long.TYPE, ResolvedVideo::class.java, List::class.java)
                .invoke(pool, context, id, ResolvedVideo(source, "", mimeType = "video/mp4"), emptyList<StreamKey>())
            val data = JSONObject().put("id", id).put("owner", "${accounts.state.value.accountId}:${accounts.state.value.selected!!.id}")
                .put("episode", "https://animeav1.com/media/offline-fixture/1").put("title", "Prueba caché MP4")
                .put("server", "https://www.mp4upload.com/embed-fixture.html").put("serverName", "MP4Upload")
                .put("state", "complete").put("bytes", cache.cacheSpace).put("total", cache.cacheSpace).put("percent", 100)
            index.edit().putString(id.toString(), data.toString()).commit()
            methods.getMethod("release", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id)
            acquired = false
            shell("svc wifi disable"); shell("svc data disable"); Thread.sleep(2_000)
            assertNull(network.activeNetwork)
            ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java)
                .putExtra("episodeUrl", "https://animeav1.com/media/offline-fixture/1").putExtra("title", "Prueba caché MP4").putExtra("offlineId", id)).use {
                rule.waitUntil(30_000) { runCatching { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                rule.onNodeWithContentDescription("Vídeo listo").assertExists()
            }
        } finally {
            if(wifi != "0") shell("svc wifi enable")
            if(mobile != "0") shell("svc data enable")
            if(acquired) methods.getMethod("release", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id)
            index.edit().remove(id.toString()).commit()
            methods.getMethod("erase", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id)
            sample.delete(); accounts.signOut()
        }
    }
}
