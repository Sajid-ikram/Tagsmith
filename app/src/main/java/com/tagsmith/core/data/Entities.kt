package com.tagsmith.core.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagsmith.core.nfc.PayloadType

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

enum class BatchStatus(val label: String) {
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETE("Complete"),
    DELIVERED("Delivered"),
    ;

    val isOpen: Boolean get() = this == RUNNING || this == PAUSED
}

/**
 * One physical tag the app has met. Written on every read and every write, so
 * "what is this card?" always has an answer after the first tap.
 */
@Entity(
    tableName = "tags",
    indices = [Index("clientId"), Index("batchId")],
)
data class TagRecord(
    @PrimaryKey val uid: String,
    val nickname: String? = null,
    val chipLabel: String,
    val technologies: String,
    val capacityBytes: Int,
    val usedBytes: Int,
    val status: TagStatus,
    val contentSummary: String,
    val clientId: Long? = null,
    val batchId: Long? = null,
    val writable: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastWrittenAt: Long? = null,
    val inInventory: Boolean = false,
)

/** Every action, success or failure, in reverse chronological order. */
@Entity(
    tableName = "history",
    indices = [Index("timestamp"), Index("uid"), Index("clientId"), Index("batchId")],
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: HistoryAction,
    val uid: String,
    val tagLabel: String,
    val chipLabel: String,
    /**
     * The client's name as it was at the time. A log records what happened;
     * renaming a client later should not rewrite last month's entries.
     */
    val clientName: String? = null,
    val clientId: Long? = null,
    val batchId: Long? = null,
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

/** A business Tagsmith supplies tags to. */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val website: String = "",
    /** ARGB. Each client gets a colour so their tags read at a glance. */
    val color: Int,
    /** A path under the app's files dir, or null for the coloured initial. */
    val logoPath: String? = null,
    val notes: String = "",
    val createdAt: Long,
)

/** A saved payload — "Google review for Oakwell", "Café guest Wi-Fi". */
@Entity(
    tableName = "templates",
    indices = [Index("clientId")],
)
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val payloadType: PayloadType,
    /** The payload, field for field, as [com.tagsmith.core.nfc.PayloadCodec] JSON. */
    val payloadJson: String,
    /** The mono one-liner a list row shows, kept so lists never decode JSON. */
    val preview: String,
    val clientId: Long? = null,
    val favourite: Boolean = false,
    val useCount: Int = 0,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
)

/**
 * A run of identical cards for one client. The payload is copied in rather
 * than referenced, so editing a template later cannot change a finished batch.
 */
@Entity(
    tableName = "batches",
    indices = [Index("clientId"), Index("status")],
)
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long? = null,
    val templateId: Long? = null,
    val payloadType: PayloadType,
    val payloadJson: String,
    val payloadPreview: String,
    val targetCount: Int,
    val writtenCount: Int = 0,
    /** Attempts that failed — shown as "retried" on the summary. */
    val failedCount: Int = 0,
    val autoVerify: Boolean,
    val autoLock: Boolean,
    val status: BatchStatus,
    val startedAt: Long,
    /** Time spent running, excluding pauses. */
    val activeMillis: Long = 0,
    /** When the current running stretch began; null while paused or finished. */
    val resumedAt: Long? = null,
    val completedAt: Long? = null,
    val deliveredAt: Long? = null,
) {
    /** `B-015` — short enough to write on the box the cards go out in. */
    val code: String get() = "B-" + id.toString().padStart(3, '0')

    fun elapsedMillis(now: Long = System.currentTimeMillis()): Long =
        activeMillis + (resumedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

/** A client row with the numbers its list row needs, computed in one query. */
data class ClientSummary(
    @Embedded val client: Client,
    val tagCount: Int,
    val lastActivityAt: Long?,
)
