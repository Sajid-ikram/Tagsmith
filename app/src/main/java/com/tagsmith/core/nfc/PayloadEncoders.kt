package com.tagsmith.core.nfc

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.ByteBuffer

/**
 * Wire formats for the payload types, kept free of Android classes so each
 * one can be tested on the JVM. [Payloads] wraps these in NDEF records.
 */

/** How a Wi-Fi tag authenticates. Android's tap-to-join only handles these two. */
enum class WifiSecurity(val label: String) {
    WPA("WPA / WPA2"),
    OPEN("Open"),
}

/** A contact card as it comes back off a tag. */
data class ContactFields(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val website: String = "",
)

/** A Wi-Fi credential as it comes back off a tag. */
data class WifiFields(
    val ssid: String,
    val security: WifiSecurity,
    val password: String,
)

object VCard {
    const val MIME = "text/vcard"
    val MIME_ALIASES = setOf("text/vcard", "text/x-vcard")

    /**
     * vCard 3.0, trimmed to the fields phones actually import. Type parameters
     * are left off on purpose: every byte counts on a 144-byte NTAG213.
     */
    fun encode(contact: ContactFields): String {
        val name = contact.name.trim()
        val company = contact.company.trim()
        val display = name.ifEmpty { company }
        val parts = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val family = if (parts.size > 1) parts.last() else ""
        val given = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.firstOrNull().orEmpty()

        return buildList {
            add("BEGIN:VCARD")
            add("VERSION:3.0")
            add("N:${escape(family)};${escape(given)};;;")
            add("FN:${escape(display)}")
            if (company.isNotEmpty()) add("ORG:${escape(company)}")
            if (contact.phone.isNotBlank()) add("TEL:${escape(contact.phone.trim())}")
            if (contact.email.isNotBlank()) add("EMAIL:${escape(contact.email.trim())}")
            if (contact.website.isNotBlank()) add("URL:${escape(contact.website.trim())}")
            add("END:VCARD")
        }.joinToString("\r\n")
    }

    /** Reads the fields back. Tolerant: unknown properties and parameters are skipped. */
    fun decode(text: String): ContactFields {
        var fields = ContactFields()
        // Unfold continuation lines (RFC 2425 §5.8.1) before splitting properties.
        val unfolded = text.replace("\r\n ", "").replace("\n ", "")
        for (line in unfolded.split("\r\n", "\n")) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val property = line.substring(0, colon).substringBefore(';').uppercase()
            val value = unescape(line.substring(colon + 1))
            fields = when (property) {
                "FN" -> fields.copy(name = value)
                "N" -> if (fields.name.isEmpty()) {
                    val (family, given) = value.split(';').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                    fields.copy(name = listOf(given, family).filter { it.isNotBlank() }.joinToString(" "))
                } else {
                    fields
                }
                "ORG" -> fields.copy(company = value.substringBefore(';'))
                "TEL" -> if (fields.phone.isEmpty()) fields.copy(phone = value) else fields
                "EMAIL" -> if (fields.email.isEmpty()) fields.copy(email = value) else fields
                "URL" -> if (fields.website.isEmpty()) fields.copy(website = value) else fields
                else -> fields
            }
        }
        return fields
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\n", "\\n")

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}

/**
 * Wi-Fi Simple Configuration, the `application/vnd.wfa.wsc` format Android's
 * NFC service reads to offer "connect to this network" on tap.
 */
object WifiCredential {
    const val MIME = "application/vnd.wfa.wsc"

    private const val CREDENTIAL: Short = 0x100E
    private const val NETWORK_INDEX: Short = 0x1026
    private const val SSID: Short = 0x1045
    private const val AUTH_TYPE: Short = 0x1003
    private const val ENCRYPTION_TYPE: Short = 0x100F
    private const val NETWORK_KEY: Short = 0x1027
    private const val MAC_ADDRESS: Short = 0x1020

    private const val AUTH_OPEN: Short = 0x0001
    private const val AUTH_WPA_PSK: Short = 0x0002
    private const val AUTH_WPA2_PSK: Short = 0x0020
    private const val AUTH_WPA_AND_WPA2_PSK: Short = 0x0022

    private const val ENCRYPTION_NONE: Short = 0x0001
    private const val ENCRYPTION_AES_TKIP: Short = 0x000C

    fun encode(wifi: WifiFields): ByteArray {
        val secured = wifi.security == WifiSecurity.WPA
        val credential = ByteArrayOutputStream().apply {
            attribute(NETWORK_INDEX, byteArrayOf(1))
            attribute(SSID, wifi.ssid.toByteArray(Charsets.UTF_8))
            attribute(AUTH_TYPE, short(if (secured) AUTH_WPA_AND_WPA2_PSK else AUTH_OPEN))
            attribute(ENCRYPTION_TYPE, short(if (secured) ENCRYPTION_AES_TKIP else ENCRYPTION_NONE))
            attribute(NETWORK_KEY, if (secured) wifi.password.toByteArray(Charsets.UTF_8) else ByteArray(0))
            // Broadcast address: the credential is not tied to one access point.
            attribute(MAC_ADDRESS, ByteArray(6) { 0xFF.toByte() })
        }.toByteArray()
        return ByteArrayOutputStream().apply { attribute(CREDENTIAL, credential) }.toByteArray()
    }

    /** Reads the first credential back, or null if the payload holds none. */
    fun decode(payload: ByteArray): WifiFields? {
        val buffer = ByteBuffer.wrap(payload)
        while (buffer.remaining() >= 4) {
            val id = buffer.short
            val length = buffer.short.toInt() and 0xFFFF
            if (length > buffer.remaining()) return null
            if (id == CREDENTIAL) return decodeCredential(buffer, length)
            buffer.position(buffer.position() + length)
        }
        return null
    }

    private fun decodeCredential(buffer: ByteBuffer, size: Int): WifiFields? {
        val end = buffer.position() + size
        var ssid: String? = null
        var key = ""
        var security = WifiSecurity.OPEN
        while (buffer.position() + 4 <= end) {
            val id = buffer.short
            val length = buffer.short.toInt() and 0xFFFF
            if (buffer.position() + length > end) return null
            val value = ByteArray(length).also { buffer.get(it) }
            when (id) {
                SSID -> ssid = String(value, Charsets.UTF_8)
                NETWORK_KEY -> key = String(value, Charsets.UTF_8)
                AUTH_TYPE -> if (length == 2) {
                    val auth = ByteBuffer.wrap(value).short
                    security = if (auth == AUTH_WPA_PSK || auth == AUTH_WPA2_PSK || auth == AUTH_WPA_AND_WPA2_PSK) {
                        WifiSecurity.WPA
                    } else {
                        WifiSecurity.OPEN
                    }
                }
            }
        }
        return ssid?.let { WifiFields(it, security, key) }
    }

    private fun ByteArrayOutputStream.attribute(id: Short, value: ByteArray) {
        write(short(id))
        write(short(value.size.toShort()))
        write(value)
    }

    private fun short(value: Short): ByteArray =
        byteArrayOf((value.toInt() shr 8).toByte(), value.toByte())
}

/** The URI-based payloads. Each opens the right app on the phone that taps it. */
object PayloadUris {

    /** Keeps what a dialler understands: digits, a leading +, and * or #. */
    fun cleanNumber(number: String): String = number.trim().filterIndexed { index, c ->
        c.isDigit() || c == '*' || c == '#' || (c == '+' && index == 0)
    }

    fun tel(number: String): String = "tel:${cleanNumber(number)}"

    fun sms(number: String, body: String): String = buildString {
        append("sms:").append(cleanNumber(number))
        if (body.isNotBlank()) append("?body=").append(encode(body))
    }

    fun mailto(address: String, subject: String, body: String): String = buildString {
        append("mailto:").append(address.trim())
        val query = buildList {
            if (subject.isNotBlank()) add("subject=${encode(subject)}")
            if (body.isNotBlank()) add("body=${encode(body)}")
        }
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
    }

    fun geo(latitude: Double, longitude: Double): String = "geo:${plain(latitude)},${plain(longitude)}"

    /** Lets the tapping phone's maps app resolve the address itself. */
    fun geoQuery(address: String): String = "geo:0,0?q=${encode(address.trim())}"

    /** Percent-encoding as URIs expect it: spaces become %20, never +. */
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun decode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)

    private fun plain(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

object Hex {
    /** Accepts `DE AD BE EF`, `de:ad:be:ef`, `0xDEADBEEF`. Null if anything else is in it. */
    fun parse(input: String): ByteArray? {
        val cleaned = input.trim().removePrefix("0x").removePrefix("0X")
            .filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        if (!cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
