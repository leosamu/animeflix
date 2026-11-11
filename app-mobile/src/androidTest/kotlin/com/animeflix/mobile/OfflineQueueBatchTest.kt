package com.animeflix.mobile

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import com.animeflix.playback.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit Wi-Fi-only live regression for the user's five-chapter batch, isolated in QA. */
@RunWith(AndroidJUnit4::class)
class OfflineQueueBatchTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun aoNoHakoBatchRotatesAndPlaysOffline() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveOfflineBatch") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        val accounts = AccountManager.create(context)
        val store = EpisodeDownloads(context)
        val ids = mutableListOf<Long>()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val wifi = shell("settings get global wifi_on")
        val mobile = shell("settings get global mobile_data")
        try {
            shell("am start -W -n ${context.packageName}/com.animeflix.mobile.UiReviewActivity --es screen offline-test")
            val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            check(network.activeNetwork != null && !network.isActiveNetworkMetered) { "La prueba requiere Wi-Fi sin coste medido." }
            accounts.beginPreview(); accounts.select(accounts.state.value.profiles.first())
            val firstFive = AnimeAv1Episodes().episodes("https://animeav1.com/media/ao-no-hako-season-2").sortedBy { it.number.toBigDecimal() }.take(5)
            assertEquals(5, firstFive.size)
            val expected = if(InstrumentationRegistry.getArguments().getString("offlinePlaybackRecheck") == "true") 2 else 5
            val episodes = if(expected == 2) listOf(firstFive.first(), firstFive.last()) else firstFive
            val sources = AnimeAv1Episodes().servers(episodes.first().pageUrl)
            val chosen = sources.first { DownloadOriginPolicy.provider(it) == "MP4Upload" }
            for(episode in episodes) {
                val source = if(expected == 2) AnimeAv1Episodes().servers(episode.pageUrl).first { DownloadOriginPolicy.provider(it) == "Voe" }.copy(name = "Voe") else chosen
                ids += store.enqueue(episode.pageUrl, "Ao no Hako S2 · Episodio ${episode.number}", source)
            }
            // Removing a queued item must not cancel the later work, as cancelling a WorkManager chain would.
            val cancelled = store.enqueue("https://animeav1.com/media/ao-no-hako-season-2/99999", "Cancelar en cola", chosen)
            store.remove(cancelled)
            assertTrue(store.list().none { it.id == cancelled })
            val deadline = SystemClock.elapsedRealtime() + 15 * 60_000
            var rows = store.list().filter { it.id in ids }
            while(rows.any { it.active } && SystemClock.elapsedRealtime() < deadline) {
                assertTrue("No debe haber descargas simultáneas", rows.count { it.status == android.app.DownloadManager.STATUS_RUNNING } <= 1)
                android.util.Log.i("AnimeFlixBatchTest", rows.joinToString("; ") { "${it.episodeUrl.substringAfterLast('/')}: ${it.serverName}, state=${it.status}, bytes=${it.bytes}, ${it.message}" })
                SystemClock.sleep(2_000)
                rows = store.list().filter { it.id in ids }
            }
            assertEquals(expected, rows.size)
            assertTrue(rows.joinToString { "${it.serverName}: ${it.statusLabel()}" }, rows.all { it.complete })
            val providers = rows.map { DownloadOriginPolicy.provider(com.animeflix.model.PlaybackServer(it.serverName, it.serverUrl)) }.distinct()
            if(expected == 5) assertTrue("La prueba debe completar capítulos de más de un origen: $providers", providers.size >= 2)
            else assertEquals(listOf("Voe"), providers)
            val boy = accounts.state.value.selected!!
            accounts.select(accounts.state.value.profiles.first { it.id != boy.id })
            assertTrue(store.list().isEmpty())
            accounts.select(boy)
            shell("svc wifi disable"); shell("svc data disable"); SystemClock.sleep(2_000)
            assertNull(network.activeNetwork)
            // One representative from each successful provider, after reopening the QA activity.
            for(entry in rows.distinctBy { it.serverName.substringAfter(" · ") }) {
                shell("am start -W -n ${context.packageName}/com.animeflix.mobile.UiReviewActivity --es screen offline-test")
                val intent = Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", entry.episodeUrl).putExtra("title", entry.title).putExtra("offlineId", entry.id)
                ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
                    rule.waitUntil(30_000) { runCatching { rule.onAllNodesWithContentDescription("Vídeo listo").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                    scenario.onActivity {
                        val player = findPlayer(it.window.decorView)!!.player!!
                        assertTrue(player.currentTracks.groups.any { track -> track.type == C.TRACK_TYPE_AUDIO })
                        player.seekTo(90_000); player.play()
                    }
                    rule.waitUntil(15_000) { var advanced = false; scenario.onActivity { advanced = findPlayer(it.window.decorView)!!.player!!.currentPosition > 91_000 }; advanced }
                }
            }
            android.util.Log.i("AnimeFlixBatchTest", "PASS: $expected complete; providers=$providers; one active; offline playback/audio tracks/seek per provider")
            Unit
        } finally {
            if(wifi != "0") shell("svc wifi enable")
            if(mobile != "0") shell("svc data enable")
            ids.forEach { store.remove(it) }
            accounts.signOut()
        }
    }
    private fun findPlayer(view: View): PlayerView? {
        if(view is PlayerView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
