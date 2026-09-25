package com.tagsmith.core.nfc

import android.nfc.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Who a write is for and where it came from. The ledger reads this to attach
 * the tag to a client, count a template's use, and advance a batch.
 */
data class WriteContext(
    val clientId: Long? = null,
    val templateId: Long? = null,
    val batchId: Long? = null,
)

/** What the next tap should do. */
sealed interface TagOperation {
    data object Read : TagOperation
    data class Write(
        val payload: NdefPayload,
        val verify: Boolean,
        val lockAfter: Boolean,
        val context: WriteContext = WriteContext(),
        /**
         * Cards this batch has already written, UID to card number. A match is
         * refused before the radio touches the tag, so one card never counts twice.
         */
        val alreadyWritten: Map<String, Int> = emptyMap(),
    ) : TagOperation

    data object Lock : TagOperation
    data object Erase : TagOperation
    data object Format : TagOperation

    val label: String
        get() = when (this) {
            Read -> "Read"
            is Write -> "Write"
            Lock -> "Lock"
            Erase -> "Erase"
            Format -> "Format"
        }
}

data class OperationResult(
    val operation: TagOperation,
    val snapshot: TagSnapshot,
    val verified: Boolean?,
)

/**
 * The tap, as a state machine. Motion carries the meaning: rings pulse in
 * [Waiting], collapse on [Detected], and resolve in [Done].
 */
sealed interface NfcPhase {
    data object Idle : NfcPhase
    data class Waiting(val operation: TagOperation) : NfcPhase
    data class Detected(val operation: TagOperation) : NfcPhase
    data class Working(val operation: TagOperation) : NfcPhase
    data class Done(val result: OperationResult) : NfcPhase
    data class Failed(
        val operation: TagOperation,
        val failure: NfcFailure,
        val snapshot: TagSnapshot?,
    ) : NfcPhase

    /** Which operation this phase belongs to, so a screen ignores other screens' taps. */
    val forOperation: TagOperation?
        get() = when (this) {
            Idle -> null
            is Waiting -> this.operation
            is Detected -> this.operation
            is Working -> this.operation
            is Done -> this.result.operation
            is Failed -> this.operation
        }

    val isInFlight: Boolean get() = this is Waiting || this is Detected || this is Working

    /** True once the tag is in the field — rings snap, progress sweeps. */
    val isEngaged: Boolean get() = this is Detected || this is Working
}

sealed interface NfcEvent {
    data class Completed(val result: OperationResult) : NfcEvent
    data class Failed(val operation: TagOperation, val failure: NfcFailure, val uid: String?) : NfcEvent
}

/** Haptics and sound, so the session can confirm a tap without owning a View. */
interface Feedback {
    fun tagDetected()
    fun success()
    fun failure()
}

/**
 * Holds the armed operation and drives it when the radio hands over a tag.
 * A single instance lives for the process; screens arm it and watch [phase].
 */
class NfcSession(
    private val scope: CoroutineScope,
    private val feedback: Feedback,
) {
    private val _phase = MutableStateFlow<NfcPhase>(NfcPhase.Idle)
    val phase: StateFlow<NfcPhase> = _phase.asStateFlow()

    private val _availability = MutableStateFlow(NfcAvailability.ABSENT)
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    private val _events = MutableSharedFlow<NfcEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<NfcEvent> = _events.asSharedFlow()

    /** The radio only listens while this is true. */
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    /** The last tag read in this process — what "look up by tap" lands on. */
    private val _lastSnapshot = MutableStateFlow<TagSnapshot?>(null)
    val lastSnapshot: StateFlow<TagSnapshot?> = _lastSnapshot.asStateFlow()

    @Volatile private var operation: TagOperation = TagOperation.Read
    @Volatile private var continuous = false
    private val inFlight = AtomicBoolean(false)

    fun setAvailability(value: NfcAvailability) {
        _availability.value = value
        if (value != NfcAvailability.READY && _armed.value) {
            _armed.value = false
        }
    }

    /**
     * Puts the radio into reader mode for [op] and moves to the waiting state.
     *
     * A [continuous] session never stands down between tags: the result goes out
     * as an event and the session goes straight back to waiting. Batch mode runs
     * this way. The radio stays in reader mode throughout, which matters — if it
     * were switched off and on between cards, a card still resting on the phone
     * would be discovered afresh and written twice.
     */
    fun arm(op: TagOperation, continuous: Boolean = false) {
        operation = op
        this.continuous = continuous
        inFlight.set(false)
        _phase.value = NfcPhase.Waiting(op)
        _armed.value = _availability.value == NfcAvailability.READY
    }

    /**
     * Swaps the operation the next tag will get without touching the radio or
     * the phase. A running batch uses this to hand over its latest written set.
     */
    fun retarget(op: TagOperation) {
        operation = op
        val current = _phase.value
        if (current is NfcPhase.Waiting) _phase.value = NfcPhase.Waiting(op)
    }

    /** Runs the armed operation again after a failure. */
    fun retry() = arm(operation, continuous)

    fun cancel() {
        inFlight.set(false)
        continuous = false
        _armed.value = false
        _phase.value = NfcPhase.Idle
    }

    /** Leaves the result on screen but stops listening. */
    fun standDown() {
        _armed.value = false
    }

    /** Called from the reader-mode callback, on a binder thread. */
    fun onTagDiscovered(tag: Tag) {
        if (!inFlight.compareAndSet(false, true)) return
        val op = operation
        val keepListening = continuous

        // A card this batch already wrote is refused before any IO — the UID is
        // on the Tag object, so the radio never has to talk to it.
        val uid = tag.id.toUidString()
        val repeatSlot = (op as? TagOperation.Write)?.alreadyWritten?.get(uid)
        if (repeatSlot != null) {
            feedback.failure()
            _events.tryEmit(NfcEvent.Failed(op, NfcFailure.AlreadyInBatch(repeatSlot), uid))
            inFlight.set(false)
            return
        }

        feedback.tagDetected()
        _phase.value = NfcPhase.Detected(op)

        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                _phase.value = NfcPhase.Working(op)
                runOperation(op, tag)
            }
            outcome.fold(
                onSuccess = { result ->
                    _lastSnapshot.value = result.snapshot
                    feedback.success()
                    _events.tryEmit(NfcEvent.Completed(result))
                    if (keepListening) {
                        _phase.value = NfcPhase.Waiting(operation)
                    } else {
                        _armed.value = false
                        _phase.value = NfcPhase.Done(result)
                    }
                },
                onFailure = { error ->
                    val failure = (error as? TagWriter.NfcOperationException)?.failure
                        ?: NfcFailure.Io(error.message ?: "Unexpected error talking to the tag.")
                    val partial = runCatching { TagReader.read(tag) }.getOrNull()
                    if (partial != null) _lastSnapshot.value = partial
                    feedback.failure()
                    _events.tryEmit(NfcEvent.Failed(op, failure, partial?.uid ?: uid))
                    if (keepListening) {
                        _phase.value = NfcPhase.Waiting(operation)
                    } else {
                        _armed.value = false
                        _phase.value = NfcPhase.Failed(op, failure, partial)
                    }
                },
            )
            inFlight.set(false)
        }
    }

    private fun runOperation(op: TagOperation, tag: Tag): Result<OperationResult> = runCatching {
        when (op) {
            // An unsupported tag still reads: identity renders even when the
            // contents cannot be decoded, so a read never fails on that alone.
            TagOperation.Read -> OperationResult(op, TagReader.read(tag), null)

            is TagOperation.Write -> {
                val message = op.payload.toMessage()
                    ?: throw TagWriter.NfcOperationException(
                        NfcFailure.Io("That payload can't be encoded as an NDEF record.")
                    )
                val result = TagWriter.write(tag, message, op.verify, op.lockAfter)
                if (op.verify && result.verified == false) {
                    throw TagWriter.NfcOperationException(
                        NfcFailure.VerificationMismatch(
                            expected = op.payload.displayValue(),
                            readBack = result.snapshot.summary,
                        )
                    )
                }
                OperationResult(op, result.snapshot, result.verified)
            }

            TagOperation.Lock -> OperationResult(op, TagWriter.lock(tag), null)
            TagOperation.Erase -> OperationResult(op, TagWriter.erase(tag), null)
            TagOperation.Format -> OperationResult(op, TagWriter.format(tag), null)
        }
    }
}
