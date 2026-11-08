package com.animeflix.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.*
import com.animeflix.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryAvailabilityTest {
    @Test fun migratesVersionFourAndKeepsInventoryAndWatchedStateAfterReopening() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val name = "library-${UUID.randomUUID()}.db"
        val media = "https://animeav1.com/media/example"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE anime (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, synopsis TEXT NOT NULL, favorite INTEGER NOT NULL, posterUrl TEXT NOT NULL DEFAULT '', sourceUrl TEXT NOT NULL DEFAULT '')")
            old.execSQL("INSERT INTO anime VALUES ('example','Mi favorito','Sinopsis',1,'',?)", arrayOf(media))
            old.execSQL("CREATE TABLE watch_history (episodeUrl TEXT NOT NULL, mediaUrl TEXT NOT NULL, number TEXT NOT NULL, openedAt INTEGER NOT NULL, watched INTEGER NOT NULL, positionMs INTEGER NOT NULL DEFAULT 0, durationMs INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(episodeUrl))")
            old.execSQL("INSERT INTO watch_history VALUES (?,?,'1',100,1,60000,120000)", arrayOf("$media/1", media))
            old.version = 4
        }
        fun open() = Room.databaseBuilder(context, AnimeDatabase::class.java, name).addMigrations(LocalAnimeRepository.MIGRATION_4_5).build()
        fun inventory(row: InventoryEntity) = EpisodeInventory(row.mediaUrl, row.episodeUrls.lines().filter { it.isNotBlank() }.map { Episode(it.substringAfterLast('/'), it) }, row.latestPublishedAt, row.checkedAt)
        try {
            val first = open()
            try {
                assertTrue(first.animeDao().observeAll().first().single().favorite)
                assertEquals(60000L, first.watchDao().observeAll().first().single().positionMs)
                first.inventoryDao().put(InventoryEntity(media, "$media/1\n$media/3", 1774542234951L, 10))
            } finally { first.close() }
            val db = open()
            try {
                fun status(row: InventoryEntity, rows: List<WatchEntity>) = episodeAvailability(inventory(row), rows.map { WatchEntry(it.episodeUrl,it.mediaUrl,it.number,watched=it.watched) })
                val saved = db.inventoryDao().observeAll().first().single()
                assertEquals(1774542234951L, saved.latestPublishedAt)
                assertEquals(1, status(saved, db.watchDao().observeAll().first()).pending)
                db.watchDao().mark(WatchEntity("$media/3",media,"3",watched=true))
                assertEquals("Al día", status(saved, db.watchDao().observeAll().first()).badge)
                val updated = saved.copy(episodeUrls = saved.episodeUrls + "\n$media/4", latestPublishedAt = null)
                db.inventoryDao().put(updated)
                assertEquals(1, status(db.inventoryDao().observeAll().first().single(), db.watchDao().observeAll().first()).pending)
                assertTrue(db.animeDao().observeAll().first().single().favorite)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
