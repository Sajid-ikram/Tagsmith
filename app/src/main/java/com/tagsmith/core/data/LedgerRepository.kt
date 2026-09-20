package com.tagsmith.core.data

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
    private val tags: TagDao,
    private val history: HistoryDao,
) {
    fun recentActivity(limit: Int = 5): Flow<List<HistoryEntry>> = history.observeRecent(limit)
    fun allActivity(): Flow<List<HistoryEntry>> = history.observeAll()
    fun activity(id: Long): Flow<HistoryEntry?> = history.observeOne(id)
    fun allTags(): Flow<List<TagRecord>> = tags.observeAll()
    fun tag(uid: String): Flow<TagRecord?> = tags.observe(uid)
    fun inventoryCount(): Flow<Int> = tags.inventoryCount()
    fun countSince(action: HistoryAction, since: Long): Flow<Int> =
        history.countSince(action, since)

    suspend fun known(uid: String): TagRecord? = tags.find(uid)

    suspend fun clearHistory() = history.clear()

    suspend fun rename(uid: String, nickname: String?) =
        tags.rename(uid, nickname?.takeIf { it.isNotBlank() })

    suspend fun addToInventory(uid: String) = tags.addToInventory(uid)

    suspend fun setStatus(uid: String, status: TagStatus) = tags.setStatus(uid, status)

    /** Records a completed operation and returns the label the UI should show. */
    suspend fun record(result: OperationResult): String {
        val snapshot = result.snapshot
        val merged = mergeTag(snapshot, wroteTo = result.operation !is TagOperation.Read)
        tags.upsert(merged)

        val label = merged.nickname ?: snapshot.uid.shortUid()
        history.insert(
            HistoryEntry(
                action = result.operation.toAction(),
                uid = snapshot.uid,
                tagLabel = label,
                chipLabel = snapshot.chipLabel,
                clientName = merged.clientName,
                detail = detailFor(result),
                payload = payloadFor(result),
                verified = result.verified,
                success = true,
                timestamp = snapshot.readAt,
            )
        )
        return label
    }

    /** Records a failure. Failures never change a tag's status. */
    suspend fun recordFailure(
        operation: TagOperation,
        failure: NfcFailure,
        snapshot: TagSnapshot?,
    ) {
        val uid = snapshot?.uid ?: "unknown"
        val existing = snapshot?.let { mergeTag(it, wroteTo = false) }
        if (existing != null) tags.upsert(existing)

        history.insert(
            HistoryEntry(
                action = operation.toAction(),
                uid = uid,
                tagLabel = existing?.nickname ?: uid.shortUid(),
                chipLabel = snapshot?.chipLabel ?: "Unknown chip",
                clientName = existing?.clientName,
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

    /**
     * Keeps everything the operator set by hand — nickname, client, inventory
     * flag — and refreshes only what the radio just told us.
     */
    private suspend fun mergeTag(snapshot: TagSnapshot, wroteTo: Boolean): TagRecord {
        val existing = tags.find(snapshot.uid)
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
            clientName = existing?.clientName,
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
