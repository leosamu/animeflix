package com.animeflix.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animeflix.data.AnimeDatabase
import com.animeflix.data.LocalAnimeRepository
import com.animeflix.data.WatchEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
    private suspend fun <T> withDatabase(database: AnimeDatabase, action: suspend (AnimeDatabase) -> T): T =
        try { action(database) } finally { database.close() }

    @Test fun migratesFavoritesAndPersistsIndependentOpenedAndWatchedState() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val name = "migration-test-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE anime (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, synopsis TEXT NOT NULL, favorite INTEGER NOT NULL, posterUrl TEXT NOT NULL DEFAULT '', sourceUrl TEXT NOT NULL DEFAULT '')")
            old.execSQL("INSERT INTO anime VALUES ('original', 'Favorito conservado', 'Sinopsis', 1, '', '')")
            old.version = 2
        }
        fun open() = Room.databaseBuilder(context, AnimeDatabase::class.java, name)
            .addMigrations(LocalAnimeRepository.MIGRATION_2_3, LocalAnimeRepository.MIGRATION_3_4, LocalAnimeRepository.MIGRATION_4_5).build()
        try {
            val episode = WatchEntity("https://animeav1.com/media/example/1", "https://animeav1.com/media/example", "1", 100)
            withDatabase(open()) { db ->
                assertTrue(db.animeDao().observeAll().first().single().favorite)
                db.watchDao().open(episode)
                db.watchDao().progress(episode.copy(positionMs = 45_000, durationMs = 120_000))
                assertFalse(db.watchDao().observeAll().first().single().watched)
                db.watchDao().mark(episode.copy(watched = true, openedAt = 0))
                db.watchDao().open(episode.copy(openedAt = 200, watched = false))
            }
            withDatabase(open()) { db ->
                val entry = db.watchDao().observeAll().first().single()
                assertTrue(entry.watched)
                assertEquals(200L, entry.openedAt)
                assertEquals(45_000L, entry.positionMs)
                assertEquals(120_000L, entry.durationMs)
                db.watchDao().mark(entry.copy(watched = false))
                assertFalse(db.watchDao().observeAll().first().single().watched)
                assertTrue(db.animeDao().observeAll().first().single().favorite)
            }
        } finally { context.deleteDatabase(name) }
    }
}
