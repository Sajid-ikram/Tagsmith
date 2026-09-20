package com.tagsmith.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a physical tag is in its life. */
enum class TagStatus(val label: String) {
    BLANK("Blank"),
    WRITTEN("Written"),
    LOCKED("Locked"),
    DEPLOYED("Deployed"),
    RETIRED("Retired"),
    FAULTY("Faulty"),
}

enum class HistoryAction(val label: String) {
    READ("Read"),
    WRITE("Write"),
    LOCK("Lock"),
    ERASE("Erase"),
    FORMAT("Format"),
}

/**
 * One physical tag the app has met. Written on every read and every write, so
 * "what is this card?" always has an answer after the first tap.
 */
@Entity(tableName = "tags")
data class TagRecord(
    @PrimaryKey val uid: String,
    val nickname: String? = null,
    val chipLabel: String,
    val technologies: String,
    val capacityBytes: Int,
    val usedBytes: Int,
    val status: TagStatus,
    val contentSummary: String,
    @ColumnInfo(defaultValue = "NULL") val clientName: String? = null,
    val writable: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastWrittenAt: Long? = null,
    val inInventory: Boolean = false,
)

/** Every action, success or failure, in reverse chronological order. */
@Entity(
    tableName = "history",
    indices = [Index("timestamp"), Index("uid")],
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: HistoryAction,
    val uid: String,
    val tagLabel: String,
    val chipLabel: String,
    val clientName: String? = null,
    /** What the row says under its title — "URL · verified", "permanent", … */
    val detail: String,
    /** Exactly what was written, for the detail view. */
    val payload: String? = null,
    /** What came back on verification, when it differed. */
    val readBack: String? = null,
    val verified: Boolean? = null,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long,
)
