package com.animeflix.mobile

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.StreamKey
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AccountManager
import com.animeflix.data.ProfileAnimeRepository
import com.animeflix.data.ResolvedVideo
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
import com.animeflix.playback.PlayerActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/** Real Media3 ENDED -> watched -> next cached file. Uses a tiny official test clip, not anime providers. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class OfflineContinuationTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun realPlayerMarksEndAdvancesOnceAndStopsBeforeMissingDownload(): Unit = runBlocking { checkContinuation(false) }
    @Test fun tvSkipButtonIsReachableAndReturnsFocusToPlayer(): Unit = runBlocking { checkContinuation(true) }
    private suspend fun checkContinuation(tvMode: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa"))
        val accounts = AccountManager.create(context)
        accounts.beginPreview(); accounts.select(accounts.state.value.profiles.first())
        val repository = ProfileAnimeRepository.create(context)
        val media = "https://animeav1.com/media/qa-auto-next"
        repository.cacheInventory(EpisodeInventory(media, (1..3).map { Episode("$it", "$media/$it") }, null, System.currentTimeMillis()))
        val sample = File(context.cacheDir, "continuation-sample.mp4")
        val stretched = File(context.cacheDir, "continuation-90s.mp4")
        val index = context.getSharedPreferences("offline_queue_v2", Context.MODE_PRIVATE)
        val skipCache = context.getSharedPreferences("aniskip_times_v1", Context.MODE_PRIVATE)
        var skipKey: String? = null
        val methods = Class.forName("com.animeflix.playback.OfflineCacheFiles")
        val pool = methods.getField("INSTANCE").get(null)
        val ids = listOf(-2_000_001L, -2_000_002L)
        try {
            val connection = URL("https://raw.githubusercontent.com/androidx/media/release/libraries/test_data/src/test/assets/media/mp4/sample.mp4")
                .openConnection().apply { connectTimeout = 20_000; readTimeout = 20_000 }
            connection.getInputStream().use { input -> sample.outputStream().use { output ->
                val buffer = ByteArray(16_384); var total = 0
                while(true) { val count = input.read(buffer); if(count < 0) break; total += count; check(total < 2_000_000); output.write(buffer, 0, count) }
            } }
            stretchTimestamps(sample, stretched)
            val metadata = MediaMetadataRetriever()
            val duration = try { metadata.setDataSource(stretched.absolutePath); checkNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() }
                finally { metadata.release() }
            skipKey = "$media/1"
            val skipResponse = JSONObject().put("found", true).put("results", org.json.JSONArray().apply {
                fun interval(type: String, start: Long, end: Long) = JSONObject().put("skipType", type).put("episodeLength", duration / 1000.0)
                    .put("interval", JSONObject().put("startTime", start / 1000.0).put("endTime", end / 1000.0))
                put(interval("recap", 0, 15_000)); put(interval("op", 15_000, 30_000)); put(interval("ed", duration - 20_000, duration - 6_000))
            })
            skipCache.edit().putString(skipKey, JSONObject().put("at", System.currentTimeMillis()).put("ttl", 604_800_000L).put("response", skipResponse.toString()).toString()).commit()
            ids.forEachIndexed { offset, id ->
                val cache = methods.getMethod("acquire", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id) as SimpleCache
                try {
                    val source = "https://offline-fixture.invalid/continuation-${offset + 1}.mp4"
                    val local = ResolvingDataSource.Factory(FileDataSource.Factory()) { it.withUri(Uri.fromFile(stretched)) }
                    ProgressiveDownloader(MediaItem.fromUri(source), CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(local), Executor { it.run() }).download(null)
                    methods.getMethod("saveSource", Context::class.java, java.lang.Long.TYPE, ResolvedVideo::class.java, List::class.java)
                        .invoke(pool, context, id, ResolvedVideo(source, "", mimeType = "video/mp4"), emptyList<StreamKey>())
                    val data = JSONObject().put("id", id).put("owner", "${accounts.state.value.accountId}:${accounts.state.value.selected!!.id}")
                        .put("episode", "$media/${offset + 1}").put("title", "QA automático ${offset + 1}")
                        .put("server", "https://www.mp4upload.com/embed-fixture.html").put("serverName", "MP4Upload")
                        .put("state", "complete").put("bytes", cache.cacheSpace).put("total", cache.cacheSpace).put("percent", 100)
                    index.edit().putString(id.toString(), data.toString()).commit()
                } finally { methods.getMethod("release", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id) }
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java).putExtra("episodeUrl", "$media/1")
                .putExtra("title", "QA automático 1").putExtra("tvMode", tvMode).putExtra("offlineId", ids.first())).use { scenario ->
                fun finishCurrent() {
                    rule.waitUntil(30_000) {
                        var ready = false
                        scenario.onActivity { activity ->
                            val player = findPlayer(activity.window.decorView)?.player
                            ready = player?.playbackState == Player.STATE_READY && player.isPlaying && player.duration >= 60_000
                        }
                        ready
                    }
                    scenario.onActivity { activity ->
                        val player = checkNotNull(findPlayer(activity.window.decorView)?.player)
                        player.seekTo(player.duration - 200); player.play()
                    }
                }
                rule.waitUntil(30_000) {
                    var ready = false
                    scenario.onActivity { ready = findPlayer(it.window.decorView)?.player?.isPlaying == true }
                    ready
                }
                scenario.onActivity { findPlayer(it.window.decorView)!!.player!!.seekTo(1_000) }
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Saltar recap").fetchSemanticsNodes().isNotEmpty() }
                val button = rule.onNodeWithText("Saltar recap").fetchSemanticsNode().boundsInRoot
                val video = rule.onNodeWithContentDescription("Vídeo listo").fetchSemanticsNode().boundsInRoot
                assertTrue("El salto debe estar dentro del reproductor", button.left >= video.left && button.top >= video.top && button.right <= video.right && button.bottom <= video.bottom)
                instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                    File(context.getExternalFilesDir(null), "recap-overlay-${if(tvMode) "tv" else "mobile"}.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    screenshot.recycle()
                }
                rule.onNodeWithText("Saltar recap").performClick()
                scenario.onActivity { assertTrue(findPlayer(it.window.decorView)!!.player!!.currentPosition >= 15_000) }
                assertFalse(repository.observeWatchHistory().first().single { it.episodeUrl == "$media/1" }.watched)
                scenario.onActivity { findPlayer(it.window.decorView)!!.player!!.seekTo(16_000) }
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Saltar opening").fetchSemanticsNodes().isNotEmpty() }
                if(tvMode) {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_DPAD_UP")).use { it.readBytes() }
                    rule.waitUntil(5_000) {
                        runCatching { rule.onNodeWithText("Saltar opening").assertIsFocused(); true }.getOrDefault(false)
                    }
                    rule.onNodeWithText("Saltar opening").assertIsFocused()
                }
                rule.onNodeWithText("Saltar opening").performClick()
                scenario.onActivity { assertTrue(findPlayer(it.window.decorView)!!.player!!.currentPosition >= 30_000) }
                if(tvMode) scenario.onActivity { assertTrue(findPlayer(it.window.decorView)!!.hasFocus()) }
                scenario.onActivity { val player = findPlayer(it.window.decorView)!!.player!!; player.seekTo(player.duration - 19_000) }
                rule.waitUntil(10_000) { rule.onAllNodesWithText("Saltar ending").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Saltar ending").performClick()
                scenario.onActivity {
                    val player = findPlayer(it.window.decorView)!!.player!!; player.pause()
                    assertTrue(player.currentPosition >= player.duration - 6_000)
                    assertTrue(player.currentPosition < player.duration - 2_000)
                }
                assertFalse(repository.observeWatchHistory().first().single { it.episodeUrl == "$media/1" }.watched)
                assertFalse(repository.observeWatchHistory().first().any { it.episodeUrl == "$media/2" && it.openedAt > 0 })
                scenario.onActivity { findPlayer(it.window.decorView)!!.player!!.play() }
                finishCurrent()
                rule.waitUntil(30_000) { runBlocking { repository.observeWatchHistory().first().any { it.episodeUrl == "$media/2" && it.openedAt > 0 } } }
                assertTrue(repository.observeWatchHistory().first().single { it.episodeUrl == "$media/1" }.watched)
                finishCurrent()
                rule.waitUntil(15_000) { runBlocking { repository.observeWatchHistory().first().any { it.episodeUrl == "$media/2" && it.watched } } }
                assertFalse(repository.observeWatchHistory().first().any { it.episodeUrl == "$media/3" && it.openedAt > 0 })
            }
        } finally {
            skipKey?.let { skipCache.edit().remove(it).commit() }
            ids.forEach { id -> index.edit().remove(id.toString()).commit(); methods.getMethod("erase", Context::class.java, java.lang.Long.TYPE).invoke(pool, context, id) }
            sample.delete(); stretched.delete(); accounts.signOut()
        }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }

    /** Stretch sample timestamps to exercise the production >=60s episode guard without a large download. */
    private fun stretchTimestamps(input: File, output: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            // Video-only fixture: stretching compressed audio packets would introduce discontinuities.
            val videoTracks = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            check(videoTracks.isNotEmpty())
            val tracks = videoTracks.associateWith { extractor.selectTrack(it); muxer.addTrack(extractor.getTrackFormat(it)) }
            val duration = videoTracks.maxOf { extractor.getTrackFormat(it).getLong(MediaFormat.KEY_DURATION) }
            val factor = (90_000_000L / duration).coerceAtLeast(1) + 1
            val firstTimestamp = extractor.sampleTime
            val buffer = ByteBuffer.allocate(2_000_000)
            val info = MediaCodec.BufferInfo()
            muxer.start()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, (extractor.sampleTime - firstTimestamp) * factor, extractor.sampleFlags)
                muxer.writeSampleData(checkNotNull(tracks[extractor.sampleTrackIndex]), buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally { extractor.release(); muxer.release() }
    }
}
