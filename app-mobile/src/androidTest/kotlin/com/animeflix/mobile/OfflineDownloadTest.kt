package com.animeflix.mobile

import android.app.DownloadManager
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import com.animeflix.playback.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in live acceptance test: a complete chapter, never runs as part of the normal unit suite.
 * Physical-device runs need a second explicit flag. Network changes are restored in finally.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDownloadTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun completeMp4SurvivesReopenPlaysLocallyAndIsIsolatedByProfile() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveOffline") == "true")
        val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")
        check(emulator || InstrumentationRegistry.getArguments().getString("allowPhysical") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        val accounts = AccountManager.create(context)
        val store = EpisodeDownloads(context)
        var id = -1L
        fun shell(cmd: String): String = instrumentation.uiAutomation.executeShellCommand(cmd).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }
        val wifi = shell("settings get global wifi_on")
        val data = shell("settings get global mobile_data")
        val url = "https://animeav1.com/media/one-piece/1"
        try {
            if(emulator) { shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard") }
            shell("am start -W -n ${context.packageName}/com.animeflix.mobile.UiReviewActivity --es screen offline-test")
            android.util.Log.i("AnimeFlixOfflineTest", "Fixture visible; preparing transfer")
            accounts.beginPreview()
            val boy = accounts.state.value.profiles.first { it.name == "Niño" }
            val girl = accounts.state.value.profiles.first { it.name == "Niña" }
            accounts.select(boy)
            val repository = ProfileAnimeRepository.create(context)
            val server = AnimeAv1Episodes().servers(url).first { Mp4UploadResolver.supports(it.embedUrl) }
            if(!emulator) check(!(context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered) { "La prueba física requiere Wi-Fi sin coste medido." }
            id = store.enqueue(url, "Prueba offline · Episodio 1", server, metered = emulator)
            val deadline = SystemClock.elapsedRealtime() + 240_000
            var entry = store.list().single { it.id == id }
            while(!entry.complete && !entry.failed && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(1_000)
                entry = store.list().single { it.id == id }
            }
            android.util.Log.i("AnimeFlixOfflineTest", "Transfer state=${entry.status}; bytes=${entry.bytes}; total=${entry.total}")
            assertTrue("Estado ${entry.status}, motivo ${entry.reason}, bytes ${entry.bytes}/${entry.total}", entry.complete)
            assertEquals(id, store.enqueue(url, entry.title, server)) // No duplicate transfer.
            val uri = checkNotNull(store.localUri(id))
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val types = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                assertTrue(types.any { it.startsWith("video/") })
                assertTrue(types.any { it.startsWith("audio/") })
            } finally { extractor.release() }
            accounts.select(girl)
            assertTrue(store.list().isEmpty())
            assertNull(store.localUri(id))
            store.remove(id) // Another profile cannot delete it.
            accounts.select(boy)
            assertNotNull(store.localUri(id))
            // Persist across a new store/activity, then disable connectivity before opening the player.
            assertTrue(EpisodeDownloads(context).list().single { it.id == id }.complete)
            shell("svc wifi disable"); shell("svc data disable")
            // API 36 TV emulator: IEthernetManager.setEthernetEnabled is transaction 13.
            // This opt-in acceptance test is intentionally pinned to that emulator image.
            if(emulator) { check(android.os.Build.VERSION.SDK_INT == 36); shell("service call ethernet 13 i32 0") }
            SystemClock.sleep(2_000)
            assertNull("La prueba requiere ausencia de red", (context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork)
            val intent = Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", url)
                .putExtra("title", entry.title).putExtra("offlineId", id).putExtra("tvMode", false)
            android.util.Log.i("AnimeFlixOfflineTest", "Opening local player; network disabled")
            ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                try { rule.waitUntil(30_000) { runCatching { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) } } catch(failure: Throwable) { rule.onRoot().printToLog("AnimeFlixOfflineUI"); throw failure }
                scenario.onActivity { activity ->
                    val player = findPlayer(activity.window.decorView)?.player ?: error("Falta el reproductor local")
                    assertEquals("file", player.currentMediaItem?.localConfiguration?.uri?.scheme)
                    player.seekTo(60_000)
                    player.play()
                }
                rule.waitUntil(15_000) {
                    var advanced = false
                    scenario.onActivity { advanced = (findPlayer(it.window.decorView)?.player?.currentPosition ?: 0) > 61_000 }
                    advanced
                }
            }
            assertTrue(repository.observeWatchHistory().first().single { it.episodeUrl == url }.positionMs >= 60_000)
            android.util.Log.i("AnimeFlixOfflineTest", "Offline first frame, seek and saved progress passed")
            // Xiaomi may block the next scenario while its invoker briefly puts the app in background.
            shell("am start -W -n ${context.packageName}/com.animeflix.mobile.UiReviewActivity --es screen offline-test")
            ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                try { rule.waitUntil(30_000) { runCatching { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) } } catch(failure: Throwable) { rule.onRoot().printToLog("AnimeFlixOfflineUI"); throw failure }
                scenario.onActivity { assertTrue((findPlayer(it.window.decorView)?.player?.currentPosition ?: 0) >= 60_000) }
            }
            File(uri.path!!).delete()
            assertNull(store.localUri(id))
            assertTrue(store.list().single { it.id == id }.failed)
            store.remove(id)
            assertTrue(store.list().isEmpty())
        } finally {
            if(emulator) shell("service call ethernet 13 i32 1")
            if(wifi != "0") shell("svc wifi enable")
            if(data != "0") shell("svc data enable")
            if(id >= 0) store.remove(id)
            accounts.signOut()
        }
    }

    private fun findPlayer(view: View): PlayerView? {
        if(view is PlayerView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
