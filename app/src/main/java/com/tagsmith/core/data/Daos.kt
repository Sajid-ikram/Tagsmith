package com.tagsmith.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Upsert
    suspend fun upsert(record: TagRecord)

    @Query("SELECT * FROM tags WHERE uid = :uid LIMIT 1")
    suspend fun find(uid: String): TagRecord?

    @Query("SELECT * FROM tags WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<TagRecord?>

    @Query("SELECT * FROM tags ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<TagRecord>>

    @Query("SELECT COUNT(*) FROM tags WHERE inInventory = 1")
    fun inventoryCount(): Flow<Int>

    @Query("UPDATE tags SET nickname = :nickname WHERE uid = :uid")
    suspend fun rename(uid: String, nickname: String?)

    @Query("UPDATE tags SET status = :status WHERE uid = :uid")
    suspend fun setStatus(uid: String, status: TagStatus)

    @Query("UPDATE tags SET inInventory = 1 WHERE uid = :uid")
    suspend fun addToInventory(uid: String)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    fun observeOne(id: Long): Flow<HistoryEntry?>

    @Query(
        "SELECT COUNT(*) FROM history " +
            "WHERE action = :action AND success = 1 AND timestamp >= :since"
    )
    fun countSince(action: HistoryAction, since: Long): Flow<Int>

    @Query("DELETE FROM history")
    suspend fun clear()
}
