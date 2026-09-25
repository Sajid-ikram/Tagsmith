package com.tagsmith.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM tags WHERE clientId = :clientId ORDER BY nickname COLLATE NOCASE, lastWrittenAt")
    fun observeByClient(clientId: Long): Flow<List<TagRecord>>

    @Query("SELECT COUNT(*) FROM tags WHERE inInventory = 1")
    fun inventoryCount(): Flow<Int>

    @Query("UPDATE tags SET nickname = :nickname WHERE uid = :uid")
    suspend fun rename(uid: String, nickname: String?)

    @Query("UPDATE tags SET status = :status WHERE uid = :uid")
    suspend fun setStatus(uid: String, status: TagStatus)

    @Query("UPDATE tags SET clientId = :clientId WHERE uid = :uid")
    suspend fun assignClient(uid: String, clientId: Long?)

    /** Delivery marks every card in the batch as out in the world. Locked stays locked. */
    @Query("UPDATE tags SET status = 'DEPLOYED' WHERE batchId = :batchId AND status = 'WRITTEN'")
    suspend fun deployBatch(batchId: Long)

    @Query("UPDATE tags SET clientId = NULL WHERE clientId = :clientId")
    suspend fun detachClient(clientId: Long)

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

    /** The most recent successful write to a tag — where its provenance comes from. */
    @Query("SELECT * FROM history WHERE uid = :uid AND action = 'WRITE' AND success = 1 ORDER BY timestamp DESC LIMIT 1")
    fun observeLastWrite(uid: String): Flow<HistoryEntry?>

    /** A batch's successful writes in order: row N is card N. */
    @Query("SELECT * FROM history WHERE batchId = :batchId AND action = 'WRITE' AND success = 1 ORDER BY timestamp ASC")
    fun observeBatchWrites(batchId: Long): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE batchId = :batchId AND action = 'WRITE' AND success = 1 ORDER BY timestamp ASC")
    suspend fun batchWrites(batchId: Long): List<HistoryEntry>

    @Query(
        "SELECT COUNT(*) FROM history " +
            "WHERE action = :action AND success = 1 AND timestamp >= :since"
    )
    fun countSince(action: HistoryAction, since: Long): Flow<Int>

    /** Clients with any activity since [since] — the "active clients" number on Home. */
    @Query("SELECT COUNT(DISTINCT clientId) FROM history WHERE clientId IS NOT NULL AND timestamp >= :since")
    fun activeClientsSince(since: Long): Flow<Int>

    @Query("UPDATE history SET clientId = NULL WHERE clientId = :clientId")
    suspend fun detachClient(clientId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface ClientDao {
    @Insert
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Delete
    suspend fun delete(client: Client)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): Client?

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<Client?>

    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Client>>

    @Query(
        "SELECT c.*, " +
            "(SELECT COUNT(*) FROM tags t WHERE t.clientId = c.id) AS tagCount, " +
            "(SELECT MAX(h.timestamp) FROM history h WHERE h.clientId = c.id) AS lastActivityAt " +
            "FROM clients c ORDER BY c.name COLLATE NOCASE"
    )
    fun observeSummaries(): Flow<List<ClientSummary>>
}

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(template: Template): Long

    @Update
    suspend fun update(template: Template)

    @Update
    suspend fun updateAll(templates: List<Template>)

    @Delete
    suspend fun delete(template: Template)

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): Template?

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<Template?>

    @Query("SELECT * FROM templates ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM templates ORDER BY sortOrder, id")
    suspend fun all(): List<Template>

    /** Favourites first, then the ones actually used — what Home's quick row shows. */
    @Query("SELECT * FROM templates ORDER BY favourite DESC, useCount DESC, sortOrder LIMIT :limit")
    fun observeQuick(limit: Int): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE clientId = :clientId ORDER BY sortOrder, id")
    fun observeByClient(clientId: Long): Flow<List<Template>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM templates")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE templates SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun recordUse(id: Long, at: Long)

    @Query("UPDATE templates SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE templates SET clientId = NULL WHERE clientId = :clientId")
    suspend fun detachClient(clientId: Long)
}

@Dao
interface BatchDao {
    @Insert
    suspend fun insert(batch: Batch): Long

    @Update
    suspend fun update(batch: Batch)

    @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): Batch?

    @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<Batch?>

    @Query("SELECT * FROM batches WHERE clientId = :clientId ORDER BY startedAt DESC")
    fun observeByClient(clientId: Long): Flow<List<Batch>>

    /** The batch Home offers to resume, if one was left running or paused. */
    @Query("SELECT * FROM batches WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<Batch?>

    @Query("SELECT * FROM batches WHERE status = 'RUNNING'")
    suspend fun running(): List<Batch>

    @Query("UPDATE batches SET clientId = NULL WHERE clientId = :clientId")
    suspend fun detachClient(clientId: Long)
}
