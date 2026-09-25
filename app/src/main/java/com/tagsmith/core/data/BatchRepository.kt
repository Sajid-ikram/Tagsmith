package com.tagsmith.core.data

import androidx.room.withTransaction
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadCodec
import com.tagsmith.core.nfc.preview
import kotlinx.coroutines.flow.Flow

/**
 * Batches and their clock. Time only runs while a batch is running, so the
 * summary's duration is time spent writing, not time the phone sat in a pocket.
 */
class BatchRepository(private val db: TagsmithDatabase) {

    private val batches = db.batches()

    fun batch(id: Long): Flow<Batch?> = batches.observe(id)
    fun open(): Flow<Batch?> = batches.observeOpen()
    fun writes(id: Long): Flow<List<HistoryEntry>> = db.history().observeBatchWrites(id)

    suspend fun find(id: Long): Batch? = batches.find(id)
    suspend fun writtenUids(id: Long): List<HistoryEntry> = db.history().batchWrites(id)

    suspend fun start(
        payload: NdefPayload,
        clientId: Long?,
        templateId: Long?,
        target: Int,
        autoVerify: Boolean,
        autoLock: Boolean,
    ): Long {
        val now = System.currentTimeMillis()
        return batches.insert(
            Batch(
                clientId = clientId,
                templateId = templateId,
                payloadType = payload.type,
                payloadJson = PayloadCodec.encode(payload),
                payloadPreview = payload.preview(),
                targetCount = target,
                autoVerify = autoVerify,
                autoLock = autoLock,
                status = BatchStatus.RUNNING,
                startedAt = now,
                resumedAt = now,
            )
        )
    }

    suspend fun pause(id: Long) = db.withTransaction {
        val batch = batches.find(id) ?: return@withTransaction
        if (batch.status != BatchStatus.RUNNING) return@withTransaction
        val now = System.currentTimeMillis()
        batches.update(batch.copy(status = BatchStatus.PAUSED, activeMillis = batch.elapsedMillis(now), resumedAt = null))
    }

    suspend fun resume(id: Long) = db.withTransaction {
        val batch = batches.find(id) ?: return@withTransaction
        if (batch.status != BatchStatus.PAUSED) return@withTransaction
        batches.update(batch.copy(status = BatchStatus.RUNNING, resumedAt = System.currentTimeMillis()))
    }

    /** Ends a batch early — the cards already written stay written. */
    suspend fun finish(id: Long) = db.withTransaction {
        val batch = batches.find(id) ?: return@withTransaction
        if (!batch.status.isOpen) return@withTransaction
        val now = System.currentTimeMillis()
        batches.update(
            batch.copy(
                status = BatchStatus.COMPLETE,
                activeMillis = batch.elapsedMillis(now),
                resumedAt = null,
                completedAt = now,
            )
        )
    }

    /** Handing the box over: every written card in the batch becomes deployed. */
    suspend fun deliver(id: Long) = db.withTransaction {
        val batch = batches.find(id) ?: return@withTransaction
        db.tags().deployBatch(id)
        batches.update(batch.copy(status = BatchStatus.DELIVERED, deliveredAt = System.currentTimeMillis()))
    }

    /**
     * A batch can only really be running while its screen is open. If the process
     * died mid-batch, the clock is stopped at the last card written and the batch
     * is offered back as paused.
     */
    suspend fun settleAfterRestart() = db.withTransaction {
        for (batch in batches.running()) {
            val lastWrite = db.history().batchWrites(batch.id).lastOrNull()?.timestamp
            val stoppedAt = maxOf(lastWrite ?: batch.resumedAt ?: batch.startedAt, batch.resumedAt ?: 0L)
            batches.update(
                batch.copy(
                    status = BatchStatus.PAUSED,
                    activeMillis = batch.elapsedMillis(stoppedAt),
                    resumedAt = null,
                )
            )
        }
    }
}

fun Batch.payload(): NdefPayload? = PayloadCodec.decode(payloadJson)
