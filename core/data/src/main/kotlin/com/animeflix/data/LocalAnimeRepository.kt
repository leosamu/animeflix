package com.animeflix.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.animeflix.model.Anime
import com.animeflix.model.AnimeRepository
import com.animeflix.model.WatchEntry
import com.animeflix.model.Episode
import com.animeflix.model.EpisodeInventory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "anime")
data class AnimeEntity(@PrimaryKey val id: String, val title: String, val synopsis: String, val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "''") val posterUrl: String = "",
    @ColumnInfo(defaultValue = "''") val sourceUrl: String = "")

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime ORDER BY title") fun observeAll(): Flow<List<AnimeEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(items: List<AnimeEntity>)
    @Query("UPDATE anime SET favorite = :favorite WHERE id = :id") suspend fun setFavorite(id: String, favorite: Boolean)
    @Query("UPDATE anime SET title = :title, synopsis = :synopsis, posterUrl = :poster, sourceUrl = :source WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, synopsis: String, poster: String, source: String)
    @Transaction
    suspend fun cache(items: List<AnimeEntity>) {
        insert(items)
        items.forEach { updateMetadata(it.id, it.title, it.synopsis, it.posterUrl, it.sourceUrl) }
    }
}

@Entity(tableName = "watch_history")
data class WatchEntity(@PrimaryKey val episodeUrl: String, val mediaUrl: String, val number: String,
    val openedAt: Long = 0, val watched: Boolean = false,
    @ColumnInfo(defaultValue = "0") val positionMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0)

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_history ORDER BY openedAt DESC, episodeUrl") fun observeAll(): Flow<List<WatchEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: WatchEntity)
    @Query("UPDATE watch_history SET openedAt = :time WHERE episodeUrl = :url") suspend fun updateOpened(url: String, time: Long)
    @Query("UPDATE watch_history SET watched = :watched WHERE episodeUrl = :url") suspend fun updateWatched(url: String, watched: Boolean)
    @Transaction suspend fun open(entry: WatchEntity) { insert(entry); updateOpened(entry.episodeUrl, entry.openedAt) }
    @Transaction suspend fun mark(entry: WatchEntity) { insert(entry); updateWatched(entry.episodeUrl, entry.watched) }
    @Transaction suspend fun markAll(entries: List<WatchEntity>) { entries.forEach { mark(it) } }
    @Query("UPDATE watch_history SET positionMs = :position, durationMs = :duration WHERE episodeUrl = :url")
    suspend fun updateProgress(url: String, position: Long, duration: Long)
    @Transaction suspend fun progress(entry: WatchEntity) { insert(entry); updateProgress(entry.episodeUrl, entry.positionMs, entry.durationMs) }
}

@Entity(tableName = "episode_inventory")
data class InventoryEntity(@PrimaryKey val mediaUrl: String, val episodeUrls: String, val latestPublishedAt: Long?, val checkedAt: Long)

@Dao
interface InventoryDao {
    @Query("SELECT * FROM episode_inventory") fun observeAll(): Flow<List<InventoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(inventory: InventoryEntity)
}

@Database(entities = [AnimeEntity::class, WatchEntity::class, InventoryEntity::class], version = 5, exportSchema = true)
abstract class AnimeDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun watchDao(): WatchDao
    abstract fun inventoryDao(): InventoryDao
}

class LocalAnimeRepository private constructor(private val dao: AnimeDao, private val watchDao: WatchDao, private val inventoryDao: InventoryDao) : AnimeRepository {
    fun observeInventories(): Flow<List<EpisodeInventory>> = inventoryDao.observeAll().map { rows -> rows.map { row ->
        EpisodeInventory(row.mediaUrl, row.episodeUrls.lineSequence().filter { AnimeAv1Episodes.isEpisodeUrl(it) }
            .map { Episode(it.substringAfterLast('/'), it) }.toList(), row.latestPublishedAt, row.checkedAt)
    } }
    suspend fun cacheInventory(inventory: EpisodeInventory) {
        require(AnimeAv1Episodes.isMediaUrl(inventory.mediaUrl) && inventory.checkedAt > 0)
        require(inventory.episodes.all { AnimeAv1Episodes.isEpisodeUrl(it.pageUrl) && it.pageUrl.substringBeforeLast('/') == inventory.mediaUrl && it.number == it.pageUrl.substringAfterLast('/') })
        inventoryDao.put(InventoryEntity(inventory.mediaUrl, inventory.episodes.distinctBy { it.pageUrl }.sortedBy { it.number.toBigDecimal() }.joinToString("\n") { it.pageUrl }, inventory.latestPublishedAt, inventory.checkedAt))
    }
    fun observeWatchHistory(): Flow<List<WatchEntry>> = watchDao.observeAll().map { rows -> rows.map {
        WatchEntry(it.episodeUrl, it.mediaUrl, it.number, it.openedAt, it.watched, it.positionMs, it.durationMs)
    } }
    suspend fun recordOpened(url: String, time: Long = System.currentTimeMillis()) {
        require(AnimeAv1Episodes.isEpisodeUrl(url))
        require(time > 0)
        watchDao.open(WatchEntity(url, url.substringBeforeLast('/'), url.substringAfterLast('/'), time))
    }
    suspend fun markWatched(url: String, watched: Boolean) {
        require(AnimeAv1Episodes.isEpisodeUrl(url))
        watchDao.mark(WatchEntity(url, url.substringBeforeLast('/'), url.substringAfterLast('/'), watched = watched))
    }
    suspend fun markPrevious(episodes: List<Episode>, target: Episode) {
        require(AnimeAv1Episodes.isEpisodeUrl(target.pageUrl))
        require(target.number == target.pageUrl.substringAfterLast('/'))
        val media = target.pageUrl.substringBeforeLast('/')
        val rows = episodes.distinctBy { it.pageUrl }.map { episode ->
            require(AnimeAv1Episodes.isEpisodeUrl(episode.pageUrl) && episode.pageUrl.substringBeforeLast('/') == media)
            require(episode.number == episode.pageUrl.substringAfterLast('/'))
            episode
        }.filter { it.number.toBigDecimal() < target.number.toBigDecimal() }
            .map { WatchEntity(it.pageUrl, media, it.number, watched = true) }
        watchDao.markAll(rows)
    }
    suspend fun saveProgress(url: String, positionMs: Long, durationMs: Long) {
        require(AnimeAv1Episodes.isEpisodeUrl(url))
        require(durationMs in 60_000..86_400_000 && positionMs in 0..durationMs)
        watchDao.progress(WatchEntity(url, url.substringBeforeLast('/'), url.substringAfterLast('/'), positionMs = positionMs, durationMs = durationMs))
    }
    override fun observeCatalog(): Flow<List<Anime>> = dao.observeAll().map { rows -> rows.map { Anime(it.id, it.title, it.synopsis, it.favorite, it.posterUrl, it.sourceUrl) } }
    suspend fun cacheCatalog(items: List<Anime>) = dao.cache(items.map { AnimeEntity(it.id, it.title, it.synopsis, false, it.posterUrl, it.sourceUrl) })
    override suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)
    override suspend fun seedDemo() = dao.insert(listOf(
        AnimeEntity("demo-1", "Viaje estelar", "Catálogo ficticio de demostración."),
        AnimeEntity("demo-2", "Guardianes del alba", "Una aventura de ejemplo, sin contenido de vídeo.")
    ))
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS episode_inventory (mediaUrl TEXT NOT NULL, episodeUrls TEXT NOT NULL, latestPublishedAt INTEGER, checkedAt INTEGER NOT NULL, PRIMARY KEY(mediaUrl))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN positionMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_history ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS watch_history (episodeUrl TEXT NOT NULL, mediaUrl TEXT NOT NULL, number TEXT NOT NULL, openedAt INTEGER NOT NULL, watched INTEGER NOT NULL, PRIMARY KEY(episodeUrl))")
            }
        }
        @Volatile private var instance: LocalAnimeRepository? = null
        fun create(context: Context): LocalAnimeRepository = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AnimeDatabase::class.java, "animeflix.db")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE anime ADD COLUMN posterUrl TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE anime ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                    }
                }, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().let { LocalAnimeRepository(it.animeDao(), it.watchDao(), it.inventoryDao()) }.also { instance = it }
        }
    }
}
