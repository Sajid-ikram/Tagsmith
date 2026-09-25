package com.tagsmith.core.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.core.net.toUri

/** Every payload Tagsmith writes, in the order the picker shows them. */
enum class PayloadType(val label: String) {
    URL("URL"),
    TEXT("Text"),
    CONTACT("Contact"),
    WIFI("Wi-Fi"),
    TEL("Tel"),
    SMS("SMS"),
    EMAIL("Email"),
    LOCATION("Location"),
    APP("App"),
    RAW("Raw"),
}

sealed interface NdefPayload {
    val type: PayloadType

    data class Url(val url: String) : NdefPayload {
        override val type get() = PayloadType.URL
    }

    data class Text(val text: String, val language: String = "en") : NdefPayload {
        override val type get() = PayloadType.TEXT
    }

    data class Contact(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val company: String = "",
        val website: String = "",
    ) : NdefPayload {
        override val type get() = PayloadType.CONTACT
        val fields get() = ContactFields(name, phone, email, company, website)
    }

    data class Wifi(
        val ssid: String = "",
        val security: WifiSecurity = WifiSecurity.WPA,
        val password: String = "",
        val hidden: Boolean = false,
    ) : NdefPayload {
        override val type get() = PayloadType.WIFI
    }

    data class Phone(val number: String = "") : NdefPayload {
        override val type get() = PayloadType.TEL
    }

    data class Sms(val number: String = "", val body: String = "") : NdefPayload {
        override val type get() = PayloadType.SMS
    }

    data class Email(val address: String = "", val subject: String = "", val body: String = "") : NdefPayload {
        override val type get() = PayloadType.EMAIL
    }

    /** Coordinates are kept as typed so a half-finished field survives editing. */
    data class Location(
        val latitude: String = "",
        val longitude: String = "",
        val address: String = "",
        val byAddress: Boolean = false,
    ) : NdefPayload {
        override val type get() = PayloadType.LOCATION
        val lat: Double? get() = latitude.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val lng: Double? get() = longitude.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    }

    /** An Android Application Record: tapping opens the app, or its Play listing. */
    data class App(val packageName: String = "", val label: String = "") : NdefPayload {
        override val type get() = PayloadType.APP
    }

    data class Raw(val mimeType: String = "", val payload: String = "", val isHex: Boolean = false) : NdefPayload {
        override val type get() = PayloadType.RAW
    }
}

/** Adds a scheme when the operator has pasted a bare host. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val hasScheme = trimmed.toUri().scheme != null
    return if (hasScheme) trimmed else "https://$trimmed"
}

private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
private val MIME = Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")

/**
 * What is wrong with what the operator has typed, in words — or null. Blank
 * required fields are not "wrong", just unfinished; [isComplete] covers those.
 */
fun NdefPayload.issue(): String? = when (this) {
    is NdefPayload.Url -> null
    is NdefPayload.Text -> null
    is NdefPayload.Contact -> email.takeIf { it.isNotBlank() && !EMAIL.matches(it.trim()) }
        ?.let { "That email address isn't complete." }

    is NdefPayload.Wifi -> when {
        security == WifiSecurity.WPA && password.isNotEmpty() && password.length !in 8..63 ->
            "WPA passwords are 8 to 63 characters."
        else -> null
    }

    is NdefPayload.Phone -> null
    is NdefPayload.Sms -> null
    is NdefPayload.Email -> address.takeIf { it.isNotBlank() && !EMAIL.matches(it.trim()) }
        ?.let { "That email address isn't complete." }

    is NdefPayload.Location -> when {
        byAddress -> null
        latitude.isNotBlank() && lat == null -> "Latitude runs from -90 to 90."
        longitude.isNotBlank() && lng == null -> "Longitude runs from -180 to 180."
        else -> null
    }

    is NdefPayload.App -> null
    is NdefPayload.Raw -> when {
        mimeType.isNotBlank() && !MIME.matches(mimeType.trim()) -> "A MIME type looks like type/subtype."
        isHex && payload.isNotBlank() && Hex.parse(payload) == null -> "That isn't valid hex — pairs of 0–9 and A–F."
        else -> null
    }
}

fun NdefPayload.isComplete(): Boolean {
    if (issue() != null) return false
    return when (this) {
        is NdefPayload.Url -> url.isNotBlank() && normalizeUrl(url).toUri().let {
            // tel:, mailto: and the like have no host but are still real URIs.
            it.host?.isNotBlank() == true || (it.scheme != null && it.scheme !in setOf("http", "https"))
        }

        is NdefPayload.Text -> text.isNotBlank()
        is NdefPayload.Contact -> name.isNotBlank() || company.isNotBlank()
        is NdefPayload.Wifi -> ssid.isNotBlank() && (security == WifiSecurity.OPEN || password.isNotEmpty())
        is NdefPayload.Phone -> PayloadUris.cleanNumber(number).count { it.isDigit() } >= 3
        is NdefPayload.Sms -> PayloadUris.cleanNumber(number).count { it.isDigit() } >= 3
        is NdefPayload.Email -> address.isNotBlank()
        is NdefPayload.Location -> if (byAddress) address.isNotBlank() else lat != null && lng != null
        is NdefPayload.App -> packageName.isNotBlank()
        is NdefPayload.Raw -> mimeType.isNotBlank() && payload.isNotEmpty()
    }
}

fun NdefPayload.toRecord(): NdefRecord? = try {
    if (!isComplete()) {
        null
    } else {
        when (this) {
            is NdefPayload.Url -> NdefRecord.createUri(normalizeUrl(url))
            is NdefPayload.Text -> NdefRecord.createTextRecord(language, text)
            is NdefPayload.Contact ->
                NdefRecord.createMime(VCard.MIME, VCard.encode(fields).toByteArray(Charsets.UTF_8))

            is NdefPayload.Wifi ->
                NdefRecord.createMime(WifiCredential.MIME, WifiCredential.encode(WifiFields(ssid, security, password)))

            is NdefPayload.Phone -> NdefRecord.createUri(PayloadUris.tel(number))
            is NdefPayload.Sms -> NdefRecord.createUri(PayloadUris.sms(number, body))
            is NdefPayload.Email -> NdefRecord.createUri(PayloadUris.mailto(address, subject, body))
            is NdefPayload.Location -> NdefRecord.createUri(
                if (byAddress) PayloadUris.geoQuery(address) else PayloadUris.geo(lat!!, lng!!)
            )

            is NdefPayload.App -> NdefRecord.createApplicationRecord(packageName.trim())
            is NdefPayload.Raw -> NdefRecord.createMime(
                mimeType.trim(),
                if (isHex) Hex.parse(payload)!! else payload.toByteArray(Charsets.UTF_8),
            )
        }
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

/** The human line shown on the success screen and stored in history. */
fun NdefPayload.displayValue(): String = when (this) {
    is NdefPayload.Url -> normalizeUrl(url)
    is NdefPayload.Text -> text
    is NdefPayload.Contact -> listOf(name, company).filter { it.isNotBlank() }.joinToString(" · ")
    is NdefPayload.Wifi -> "$ssid · ${security.label}"
    is NdefPayload.Phone -> number.trim()
    is NdefPayload.Sms -> listOf(number.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    is NdefPayload.Email -> listOf(address.trim(), subject.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    is NdefPayload.Location -> if (byAddress) address.trim() else "${latitude.trim()}, ${longitude.trim()}"
    is NdefPayload.App -> label.ifBlank { packageName }
    is NdefPayload.Raw -> "${mimeType.trim()} · ${if (isHex) Hex.parse(payload)?.size ?: 0 else payload.length} bytes"
}

/**
 * The mono one-liner a template row shows: what goes on the tag, in the form a
 * technician would recognise — `geo:51.45,-2.58`, `WIFI:S:Guest;T:WPA`.
 */
fun NdefPayload.preview(): String = when (this) {
    is NdefPayload.Url -> normalizeUrl(url).removePrefix("https://").removePrefix("http://")
    is NdefPayload.Text -> text.lineSequence().firstOrNull().orEmpty()
    is NdefPayload.Contact -> listOf(name, company).filter { it.isNotBlank() }.joinToString(" · ")
    is NdefPayload.Wifi -> "WIFI:S:$ssid;T:${if (security == WifiSecurity.WPA) "WPA" else "nopass"}"
    is NdefPayload.Phone -> "tel:${PayloadUris.cleanNumber(number)}"
    is NdefPayload.Sms -> "sms:${PayloadUris.cleanNumber(number)}"
    is NdefPayload.Email -> "mailto:${address.trim()}" + if (subject.isNotBlank()) " · subject" else ""
    is NdefPayload.Location ->
        if (byAddress) "geo:?q=${address.trim()}" else "geo:${latitude.trim()},${longitude.trim()}"

    is NdefPayload.App -> packageName
    is NdefPayload.Raw -> mimeType.trim()
}
