package com.tagsmith.core.nfc

import androidx.core.net.toUri
import android.nfc.NdefMessage
import android.nfc.NdefRecord

/**
 * The payload types v1 writes. The picker in the write screen shows the rest as
 * disabled chips so the shape of the finished product stays visible.
 */
enum class PayloadType(val label: String, val available: Boolean) {
    URL("URL", true),
    TEXT("Text", true),
    CONTACT("Contact", false),
    WIFI("Wi-Fi", false),
    TEL("Tel", false),
    SMS("SMS", false),
    EMAIL("Email", false),
    LOCATION("Location", false),
    APP("App", false),
    RAW("Raw", false),
}

sealed interface NdefPayload {
    val type: PayloadType

    data class Url(val url: String) : NdefPayload {
        override val type = PayloadType.URL
    }

    data class Text(val text: String, val language: String = "en") : NdefPayload {
        override val type = PayloadType.TEXT
    }
}

/** Adds a scheme when the operator has pasted a bare host. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val hasScheme = trimmed.toUri().scheme != null
    return if (hasScheme) trimmed else "https://$trimmed"
}

fun NdefPayload.isComplete(): Boolean = when (this) {
    is NdefPayload.Url -> url.isNotBlank() && normalizeUrl(url).toUri().host?.isNotBlank() == true
    is NdefPayload.Text -> text.isNotBlank()
}

fun NdefPayload.toRecord(): NdefRecord? = try {
    when (this) {
        is NdefPayload.Url -> if (url.isBlank()) null else NdefRecord.createUri(normalizeUrl(url))
        is NdefPayload.Text -> if (text.isBlank()) null else NdefRecord.createTextRecord(language, text)
    }
} catch (_: IllegalArgumentException) {
    null
}

fun NdefPayload.toMessage(): NdefMessage? = toRecord()?.let { NdefMessage(arrayOf(it)) }

/**
 * The number under the byte counter: what this payload costs on a tag, including
 * NDEF framing — not the length of the string the operator typed.
 */
fun NdefPayload.byteSize(): Int = toMessage()?.toByteArray()?.size ?: 0

/** The single-record empty message an erase writes. */
fun emptyNdefMessage(): NdefMessage =
    NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))

/** The value shown on the success screen and stored in history. */
fun NdefPayload.displayValue(): String = when (this) {
    is NdefPayload.Url -> normalizeUrl(url)
    is NdefPayload.Text -> text
}
