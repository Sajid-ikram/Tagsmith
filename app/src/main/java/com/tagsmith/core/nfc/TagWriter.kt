package com.tagsmith.core.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

/** The result of one write attempt, before it becomes a history row. */
data class WriteResult(
    val snapshot: TagSnapshot,
    val verified: Boolean?,
    val locked: Boolean,
    val formatted: Boolean,
)

/**
 * Writes, erases, formats and locks. Every entry point is blocking and throws
 * [NfcOperationException] carrying the copy the screen shows.
 */
object TagWriter {

    class NfcOperationException(val failure: NfcFailure) : Exception(failure.headline)

    fun write(
        tag: Tag,
        message: NdefMessage,
        verify: Boolean,
        lockAfter: Boolean,
    ): WriteResult = runGuarded {
        val payloadSize = message.toByteArray().size
        val ndef = Ndef.get(tag)

        if (ndef != null) {
            var verified: Boolean? = null
            ndef.use { connection ->
                connection.connect()
                if (!connection.isWritable) throw NfcOperationException(NfcFailure.Locked)
                if (connection.maxSize < payloadSize) {
                    throw NfcOperationException(NfcFailure.TooSmall(payloadSize, connection.maxSize))
                }
                connection.writeNdefMessage(message)
                if (verify) {
                    val readBack = connection.ndefMessage
                    verified = readBack?.toByteArray().contentEquals(message.toByteArray())
                }
                if (lockAfter) connection.makeReadOnly()
            }
            // A mismatch is not thrown here: the caller holds the payload the
            // operator typed, so it builds the expected-vs-read-back comparison.
            return@runGuarded WriteResult(TagReader.read(tag), verified, lockAfter, formatted = false)
        }

        // Unformatted stock: format and write in the same tap.
        val formatable = NdefFormatable.get(tag)
            ?: throw NfcOperationException(NfcFailure.Unsupported(tag.techList.firstOrNull()?.substringAfterLast('.') ?: "This tag"))

        formatable.use { connection ->
            connection.connect()
            if (lockAfter) connection.formatReadOnly(message) else connection.format(message)
        }
        val snapshot = TagReader.read(tag)
        val verified = if (verify) {
            snapshot.records.isNotEmpty() && snapshot.usedBytes == payloadSize
        } else {
            null
        }
        WriteResult(snapshot, verified, lockAfter, formatted = true)
    }

    /** Writes a single empty record — the tag stays formatted and writable. */
    fun erase(tag: Tag): TagSnapshot = runGuarded {
        val ndef = Ndef.get(tag) ?: throw NfcOperationException(NfcFailure.Unformatted)
        ndef.use { connection ->
            connection.connect()
            if (!connection.isWritable) throw NfcOperationException(NfcFailure.Locked)
            connection.writeNdefMessage(emptyNdefMessage())
        }
        TagReader.read(tag)
    }

    /** Gives an unformatted tag an empty NDEF message so it can be written to. */
    fun format(tag: Tag): TagSnapshot = runGuarded {
        val formatable = NdefFormatable.get(tag)
            ?: throw NfcOperationException(NfcFailure.Unsupported("This tag"))
        formatable.use { connection ->
            connection.connect()
            connection.format(emptyNdefMessage())
        }
        TagReader.read(tag)
    }

    /** Permanent. There is no undo, which is why the screen before it is heavy. */
    fun lock(tag: Tag): TagSnapshot = runGuarded {
        val ndef = Ndef.get(tag) ?: throw NfcOperationException(NfcFailure.Unformatted)
        ndef.use { connection ->
            connection.connect()
            if (!connection.canMakeReadOnly()) {
                throw NfcOperationException(NfcFailure.Unsupported("This chip"))
            }
            if (!connection.isWritable) throw NfcOperationException(NfcFailure.Locked)
            connection.makeReadOnly()
        }
        TagReader.read(tag)
    }

    /** Maps the radio's exceptions onto the failures the screens know how to draw. */
    private inline fun <T> runGuarded(block: () -> T): T = try {
        block()
    } catch (e: NfcOperationException) {
        throw e
    } catch (_: TagLostException) {
        throw NfcOperationException(NfcFailure.TagLost)
    } catch (e: FormatException) {
        throw NfcOperationException(NfcFailure.Io(e.message ?: "The tag rejected the NDEF message."))
    } catch (_: IOException) {
        throw NfcOperationException(NfcFailure.TagLost)
    } catch (_: SecurityException) {
        throw NfcOperationException(NfcFailure.Io("The tag went out of range before Tagsmith could talk to it."))
    } catch (e: Exception) {
        throw NfcOperationException(NfcFailure.Io(e.message ?: "Unexpected error talking to the tag."))
    }
}
