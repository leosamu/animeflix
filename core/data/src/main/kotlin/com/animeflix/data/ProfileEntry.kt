package com.animeflix.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** One field per row: a progress update cannot overwrite another device's watched mark. */
@Entity(tableName = "profile_entries", primaryKeys = ["kind", "itemKey"])
data class ProfileEntry(
    val kind: String, val itemKey: String, val value: String,
    val revision: String = UUID.randomUUID().toString(), val dirty: Boolean = true
)

@Dao
interface ProfileEntryDao {
    @Query("SELECT * FROM profile_entries") fun observe(): Flow<List<ProfileEntry>>
    @Query("SELECT * FROM profile_entries WHERE dirty = 1") suspend fun pending(): List<ProfileEntry>
    @Query("SELECT * FROM profile_entries WHERE kind = :kind AND itemKey = :key") suspend fun get(kind: String, key: String): ProfileEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: ProfileEntry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAll(entries: List<ProfileEntry>)
    @Query("UPDATE profile_entries SET dirty = 0 WHERE kind = :kind AND itemKey = :key AND revision = :revision")
    suspend fun acknowledge(kind: String, key: String, revision: String)
    @Transaction suspend fun receive(entries: List<ProfileEntry>) {
        entries.forEach { if (get(it.kind, it.itemKey)?.dirty != true) put(it.copy(dirty = false)) }
    }
}
