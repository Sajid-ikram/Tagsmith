package com.tagsmith.core.data

import androidx.room.withTransaction
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.core.nfc.OperationResult
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.core.nfc.displayValue
import com.tagsmith.core.nfc.shortUid
import kotlinx.coroutines.flow.Flow

/**
 * The ledger: every tap the app has seen, and what it did.
 * Reads and writes both land here, which is what makes "tap it and find out"
 * work on a card that left the workshop three months ago.
 */
class LedgerRepository(
    private val db: TagsmithDatabase,
) {
    private val tags = db.tags()
    private val history = db.history()
    private val clients = db.clients()
    private val templates = db.templates()
    private val batches = db.batches()

    fun recentActivity(limit: Int = 5): Flow<List<HistoryEntry>> = history.observeRecent(limit)
    fun allActivity(): Flow<List<HistoryEntry>> = history.observeAll()
    fun activity(id: Long): Flow<HistoryEntry?> = history.observeOne(id)
    fun allTags(): Flow<List<TagRecord>> = tags.observeAll()
    fun tag(uid: String): Flow<TagRecord?> = tags.observe(uid)
    fun lastWrite(uid: String): Flow<HistoryEntry?> = history.observeLastWrite(uid)
    fun inventoryCount(): Flow<Int> = tags.inventoryCount()
    fun countSince(action: HistoryAction, since: Long): Flow<Int> =
        history.countSince(action, since)

    fun activeClientsSince(since: Long): Flow<Int> = history.activeClientsSince(since)

    suspend fun known(uid: String): TagRecord? = tags.find(uid)

    suspend fun clearHistory() = history.clear()

    suspend fun rename(uid: String, nickname: String?) =
        tags.rename(uid, nickname?.takeIf { it.isNotBlank() })

    suspend fun addToInventory(uid: String) = tags.addToInventory(uid)

    suspend fun setStatus(uid: String, status: TagStatus) = tags.setStatus(uid, status)

    suspend fun assignClient(uid: String, clientId: Long?) = tags.assignClient(uid, clientId)

    /**
     * Records a completed operation. One transaction covers the tag, the history
     * row, the template's use count and the batch counter, so a batch can never
     * show a card as written without the ledger agreeing.
     */
    suspend fun record(result: OperationResult): String = db.withTransaction {
        val snapshot = result.snapshot
        val write = result.operation as? TagOperation.Write
        val context = write?.context
        val existing = tags.find(snapshot.uid)
        var merged = mergeTag(existing, snapshot, wroteTo = result.operation !is TagOperation.Read)
        if (write != null && !snapshot.locked) {
            // The read-back after a write can come from Android's cache of the tag
            // as it was *before* the write, if the live read fails. The write itself
            // succeeded, so record what was written rather than a stale "Blank".
            merged = merged.copy(status = TagStatus.WRITTEN, contentSummary = write.payload.displayValue())
        }

        // An explicit client wins; otherwise the tag keeps the one it had.
        val clientId = context?.clientId ?: existing?.clientId
        val client = clientId?.let { clients.find(it) }
        merged = merged.copy(clientId = client?.id)

        // A card tapped in the instant after the batch filled up is still a real
        // write, but it is not card N+1 of a finished batch.
        val batch = context?.batchId?.let { batches.find(it) }?.takeIf { it.status.isOpen }
        if (batch != null) {
            val slot = batch.writtenCount + 1
            merged = merged.copy(
                batchId = batch.id,
                nickname = batchNickname(client, batch, slot),
            )
            val now = System.currentTimeMillis()
            val complete = slot >= batch.targetCount
            batches.update(
                batch.copy(
                    writtenCount = slot,
                    status = if (complete) BatchStatus.COMPLETE else batch.status,
                    activeMillis = if (complete) batch.elapsedMillis(now) else batch.activeMillis,
                    resumedAt = if (complete) null else batch.resumedAt,
                    completedAt = if (complete) now else batch.completedAt,
                )
            )
        }

        tags.upsert(merged)
        context?.templateId?.let { templates.recordUse(it, snapshot.readAt) }

        val label = merged.nickname ?: snapshot.uid.shortUid()
        history.insert(
            HistoryEntry(
                action = result.operation.toAction(),
                uid = snapshot.uid,
                tagLabel = label,
                chipLabel = snapshot.chipLabel,
                clientName = client?.name,
                clientId = client?.id,
                batchId = batch?.id,
                detail = detailFor(result),
                payload = payloadFor(result),
                verified = result.verified,
                success = true,
                timestamp = snapshot.readAt,
            )
        )
        label
    }

    /** Records a failure. Failures never change a tag's status, and never count. */
    suspend fun recordFailure(
        operation: TagOperation,
        failure: NfcFailure,
        snapshot: TagSnapshot?,
    ) = db.withTransaction {
        val uid = snapshot?.uid ?: "unknown"
        val existing = snapshot?.let { tags.find(it.uid) }
        val merged = snapshot?.let { mergeTag(existing, it, wroteTo = false) }
        if (merged != null) tags.upsert(merged)

        val context = (operation as? TagOperation.Write)?.context
        val client = (context?.clientId ?: existing?.clientId)?.let { clients.find(it) }
        val batch = context?.batchId?.let { batches.find(it) }
        if (batch != null) batches.update(batch.copy(failedCount = batch.failedCount + 1))

        history.insert(
            HistoryEntry(
                action = operation.toAction(),
                uid = uid,
                tagLabel = merged?.nickname ?: uid.shortUid(),
                chipLabel = snapshot?.chipLabel ?: "Unknown chip",
                clientName = client?.name,
                clientId = client?.id,
                batchId = batch?.id,
                detail = failure.headline,
                payload = (operation as? TagOperation.Write)?.payload?.displayValue(),
                readBack = (failure as? NfcFailure.VerificationMismatch)?.readBack,
                verified = if (failure is NfcFailure.VerificationMismatch) false else null,
                success = false,
                errorMessage = failure.detail,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /** `Oakwell · card 07` — the design's naming, taken from the client's first word. */
    private fun batchNickname(client: Client?, batch: Batch, slot: Int): String {
        val owner = client?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: batch.code
        return "$owner · card ${slot.toString().padStart(2, '0')}"
    }

    /**
     * Keeps everything the operator set by hand — nickname, client, inventory
     * flag — and refreshes only what the radio just told us.
     */
    private fun mergeTag(existing: TagRecord?, snapshot: TagSnapshot, wroteTo: Boolean): TagRecord {
        val status = when {
            snapshot.locked -> TagStatus.LOCKED
            existing?.status == TagStatus.DEPLOYED && !wroteTo -> TagStatus.DEPLOYED
            existing?.status == TagStatus.FAULTY -> TagStatus.FAULTY
            existing?.status == TagStatus.RETIRED -> TagStatus.RETIRED
            snapshot.isBlank -> TagStatus.BLANK
            else -> TagStatus.WRITTEN
        }
        return TagRecord(
            uid = snapshot.uid,
            nickname = existing?.nickname,
            chipLabel = snapshot.chipLabel,
            technologies = snapshot.technologies.joinToString(" · "),
            capacityBytes = snapshot.capacityBytes,
            usedBytes = snapshot.usedBytes,
            status = status,
            contentSummary = snapshot.summary,
            clientId = existing?.clientId,
            batchId = existing?.batchId,
            writable = snapshot.writable,
            firstSeenAt = existing?.firstSeenAt ?: snapshot.readAt,
            lastSeenAt = snapshot.readAt,
            lastWrittenAt = if (wroteTo) snapshot.readAt else existing?.lastWrittenAt,
            inInventory = existing?.inInventory ?: false,
        )
    }

    private fun detailFor(result: OperationResult): String = when (val op = result.operation) {
        TagOperation.Read -> "${result.snapshot.chipLabel} · ${result.snapshot.summary}"
        is TagOperation.Write -> buildString {
            append(op.payload.type.label)
            when (result.verified) {
                true -> append(" · verified")
                false -> append(" · not verified")
                null -> Unit
            }
            if (op.lockAfter) append(" · locked")
        }

        TagOperation.Lock -> "permanent"
        TagOperation.Erase -> "empty NDEF written"
        TagOperation.Format -> "NdefFormatable → NDEF"
    }

    private fun payloadFor(result: OperationResult): String? = when (val op = result.operation) {
        is TagOperation.Write -> op.payload.displayValue()
        else -> result.snapshot.records.firstOrNull()?.display
    }

    private fun TagOperation.toAction(): HistoryAction = when (this) {
        TagOperation.Read -> HistoryAction.READ
        is TagOperation.Write -> HistoryAction.WRITE
        TagOperation.Lock -> HistoryAction.LOCK
        TagOperation.Erase -> HistoryAction.ERASE
        TagOperation.Format -> HistoryAction.FORMAT
    }
}
