package com.animeflix.mobile

import android.content.Context
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import com.animeflix.model.PlaybackServer
import com.animeflix.playback.EpisodeDownloads
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineIndexTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".qa")) }

    @Test fun incompleteFilesAreNeverPlayableAndAnotherProfileCannotRemoveThem() = runBlocking {
        val accounts = AccountManager.create(context)
        val store = EpisodeDownloads(context)
        val index = context.getSharedPreferences("offline_episodes_v1", Context.MODE_PRIVATE)
        val id = Long.MAX_VALUE - 1
        val name = "animeflix-00000000-0000-0000-0000-000000000001.mp4"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), name)
        try {
            accounts.beginPreview()
            val boy = accounts.state.value.profiles.first { it.name == "Niño" }
            val girl = accounts.state.value.profiles.first { it.name == "Niña" }
            accounts.select(boy)
            file.parentFile!!.mkdirs()
            file.writeBytes(ByteArray(64))
            val json = JSONObject().put("owner", "${accounts.state.value.accountId}:${boy.id}")
                .put("episode", "https://animeav1.com/media/example/1").put("title", "Incompleto")
                .put("server", "https://www.mp4upload.com/embed-example.html").put("file", name)
            index.edit().putString(id.toString(), json.toString()).commit()
            assertTrue(store.list().single { it.id == id }.failed)
            assertNull(store.localUri(id))
            accounts.select(girl)
            assertTrue(store.list().none { it.id == id })
            assertNull(store.localUri(id))
            store.remove(id)
            assertTrue(file.exists())
            accounts.select(boy)
            store.remove(id)
            assertFalse(file.exists())
            assertFalse(index.contains(id.toString()))
        } finally {
            file.delete(); index.edit().remove(id.toString()).commit(); accounts.signOut()
        }
    }

    @Test fun unsupportedSourcesAreRejectedBeforeResolvingOrQueuing() = runBlocking {
        val store = EpisodeDownloads(context)
        try {
            store.enqueue("https://animeav1.com/media/example/1", "No compatible", PlaybackServer("HLS", "https://example.invalid/master.m3u8"))
            fail("No se debe añadir una fuente HLS a la descarga progresiva")
        } catch(expected: IllegalArgumentException) { /* Provider capability gate precedes all network access. */ }
    }
}
