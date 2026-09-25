package com.tagsmith.core.nfc

/**
 * The states that actually bite, each carrying the copy from the screen board.
 * [headline] is set at hero size; [detail] sits under it.
 */
sealed class NfcFailure(
    val kicker: String,
    val headline: String,
    val detail: String,
    val recoverable: Boolean = true,
) {
    data object TagLost : NfcFailure(
        kicker = "CONNECTION LOST",
        headline = "The tag moved too soon",
        detail = "Nothing was changed. Hold it flat against the back of the phone for about a second.",
    )

    data object Locked : NfcFailure(
        kicker = "READ-ONLY TAG",
        headline = "This tag is locked",
        detail = "It was made permanently read-only, so nothing can rewrite, erase or reformat it — not even another app.",
        recoverable = false,
    )

    data object Unformatted : NfcFailure(
        kicker = "NO NDEF MESSAGE",
        headline = "This tag has no NDEF message yet",
        detail = "It needs formatting before anything can be written to it. Tagsmith can do that in the same tap.",
    )

    data class TooSmall(val needed: Int, val capacity: Int) : NfcFailure(
        kicker = "PAYLOAD TOO LARGE",
        headline = "This tag is too small",
        detail = "This tag holds $capacity bytes; your payload is $needed. Shorten the URL, or swap to a larger chip.",
    )

    data class Unsupported(val tech: String) : NfcFailure(
        kicker = "UNSUPPORTED TAG",
        headline = "Tagsmith can't write this tag",
        detail = "$tech doesn't carry an NDEF message Tagsmith can work with. The identity below still reads.",
        recoverable = false,
    )

    data class VerificationMismatch(val expected: String, val readBack: String) : NfcFailure(
        kicker = "VERIFICATION MISMATCH",
        headline = "Written, but it read back wrong",
        detail = "The tag accepted the write and returned something else. Rewrite it before you hand it over.",
    )

    data class Io(val reason: String) : NfcFailure(
        kicker = "TAG ERROR",
        headline = "Couldn't finish the operation",
        detail = reason,
    )

    /**
     * Batch mode saw a card it has already written. Nothing is touched, and the
     * counter does not move — the same card must never count twice.
     */
    data class AlreadyInBatch(val slot: Int) : NfcFailure(
        kicker = "ALREADY WRITTEN",
        headline = "That's card ${slot.toString().padStart(2, '0')} again",
        detail = "It was written earlier in this batch. Put it aside and tap the next blank card.",
    )
}
