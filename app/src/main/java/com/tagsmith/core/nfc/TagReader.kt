package com.tagsmith.core.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import java.io.IOException
import java.nio.charset.Charset

/** Turns a live [Tag] into a [TagSnapshot]. Blocking — call it off the main thread. */
object TagReader {

    fun read(tag: Tag): TagSnapshot {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val chip = detectChip(tag, techs)
        val ndef = Ndef.get(tag)
        val formattable = NdefFormatable.get(tag) != null

        if (ndef == null) {
            // Either unformatted, or a family whose contents Tagsmith cannot decode.
            return TagSnapshot(
                uid = tag.id.toUidString(),
                chip = chip,
                chipLabel = chip.label,
                technologies = techs,
                capacityBytes = chip.nominalCapacity,
                usedBytes = 0,
                writable = formattable,
                locked = false,
                canMakeReadOnly = false,
                formatted = false,
                formattable = formattable,
                supported = formattable,
                records = emptyList(),
                rawHex = "",
                readAt = System.currentTimeMillis(),
            )
        }

        var message: NdefMessage? = null
        var writable = false
        var canMakeReadOnly = false
        var capacity = chip.nominalCapacity

        ndef.use { connection ->
            connection.connect()
            capacity = connection.maxSize
            writable = connection.isWritable
            canMakeReadOnly = connection.canMakeReadOnly()
            // A live read beats the cached message: the cache predates any write
            // we did a moment ago.
            message = try {
                connection.ndefMessage
            } catch (_: Exception) {
                connection.cachedNdefMessage
            }
        }

        val decoded = message
        val bytes = decoded?.toByteArray() ?: ByteArray(0)
        return TagSnapshot(
            uid = tag.id.toUidString(),
            chip = chip,
            chipLabel = chip.label,
            technologies = techs,
            capacityBytes = if (capacity > 0) capacity else chip.nominalCapacity,
            usedBytes = bytes.size,
            writable = writable,
            locked = !writable,
            canMakeReadOnly = canMakeReadOnly,
            formatted = true,
            formattable = formattable,
            supported = true,
            records = decoded?.records?.map { it.toView() } ?: emptyList(),
            rawHex = bytes.toHexDump(),
            readAt = System.currentTimeMillis(),
        )
    }

    /**
     * NTAG chips answer GET_VERSION with their storage size, which is the only
     * reliable way to tell a 213 from a 215 before reading anything.
     */
    private fun detectChip(tag: Tag, techs: List<String>): ChipType {
        if ("MifareClassic" in techs) {
            return when (MifareClassic.get(tag)?.size) {
                MifareClassic.SIZE_4K -> ChipType.MIFARE_CLASSIC_4K
                else -> ChipType.MIFARE_CLASSIC_1K
            }
        }
        if ("IsoDep" in techs) return ChipType.DESFIRE

        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            val version = try {
                nfcA.use {
                    it.connect()
                    it.transceive(byteArrayOf(GET_VERSION))
                }
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
                null
            }
            if (version != null && version.size >= 8) {
                val productType = version[2].toInt() and 0xFF
                val storage = version[6].toInt() and 0xFF
                return when {
                    productType == 0x04 && storage == 0x0F -> ChipType.NTAG213
                    productType == 0x04 && storage == 0x11 -> ChipType.NTAG215
                    productType == 0x04 && storage == 0x13 -> ChipType.NTAG216
                    productType == 0x03 && storage == 0x0B -> ChipType.ULTRALIGHT_EV1_48
                    productType == 0x03 && storage == 0x0E -> ChipType.ULTRALIGHT_EV1_128
                    else -> ChipType.UNKNOWN
                }
            }
        }

        if ("MifareUltralight" in techs) {
            return when (MifareUltralight.get(tag)?.type) {
                MifareUltralight.TYPE_ULTRALIGHT_C -> ChipType.ULTRALIGHT_EV1_48
                else -> ChipType.ULTRALIGHT
            }
        }
        return ChipType.UNKNOWN
    }

    /**
     * The on-tag cost of one record: header, type length, payload length
     * (short form under 256 bytes), optional id length, then the fields.
     */
    private fun NdefRecord.encodedSize(): Int {
        val typeLength = type?.size ?: 0
        val idLength = id?.size ?: 0
        val payloadLength = payload?.size ?: 0
        return 1 + 1 + (if (payloadLength < 256) 1 else 4) +
            (if (idLength > 0) 1 else 0) + typeLength + idLength + payloadLength
    }

    private fun NdefRecord.toView(): NdefRecordView {
        val raw = payload ?: ByteArray(0)
        val size = encodedSize()
        return when {
            tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) -> {
                val uri = decodeUri(raw)
                NdefRecordView(
                    kind = RecordKind.URI,
                    typeLabel = "URI · WELL-KNOWN",
                    display = uri,
                    link = uri,
                    rawHex = raw.toHexDump(),
                    sizeBytes = size,
                )
            }

            tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
                NdefRecordView(
                    kind = RecordKind.TEXT,
                    typeLabel = "TEXT · WELL-KNOWN",
                    display = decodeText(raw),
                    rawHex = raw.toHexDump(),
                    sizeBytes = size,
                )
            }

            tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> {
                NdefRecordView(
                    kind = RecordKind.SMART_POSTER,
                    typeLabel = "SMART POSTER",
                    display = runCatching {
                        NdefMessage(raw).records.joinToString(" · ") { it.toView().display }
                    }.getOrElse { "Smart poster" },
                    rawHex = raw.toHexDump(),
                    sizeBytes = size,
                )
            }

            tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                val uri = String(type, Charsets.UTF_8)
                NdefRecordView(RecordKind.URI, "ABSOLUTE URI", uri, uri, raw.toHexDump(), size)
            }

            tnf == NdefRecord.TNF_MIME_MEDIA -> {
                val mime = String(type, Charsets.UTF_8)
                NdefRecordView(
                    kind = RecordKind.MIME,
                    typeLabel = "MIME · " + mime.uppercase(),
                    display = if (mime.startsWith("text/")) {
                        String(raw, Charsets.UTF_8)
                    } else {
                        "${raw.size} bytes"
                    },
                    rawHex = raw.toHexDump(),
                    sizeBytes = size,
                )
            }

            tnf == NdefRecord.TNF_EXTERNAL_TYPE -> {
                val ext = String(type, Charsets.UTF_8)
                val isAar = ext.equals("android.com:pkg", ignoreCase = true)
                NdefRecordView(
                    kind = if (isAar) RecordKind.ANDROID_APP else RecordKind.EXTERNAL,
                    typeLabel = if (isAar) "ANDROID APPLICATION RECORD" else "EXTERNAL · " + ext.uppercase(),
                    display = String(raw, Charsets.UTF_8),
                    rawHex = raw.toHexDump(),
                    sizeBytes = size,
                )
            }

            tnf == NdefRecord.TNF_EMPTY ->
                NdefRecordView(RecordKind.EMPTY, "EMPTY", "Empty record", null, "", size)

            else -> NdefRecordView(
                kind = RecordKind.UNKNOWN,
                typeLabel = "UNKNOWN · TNF $tnf",
                display = "${raw.size} bytes",
                rawHex = raw.toHexDump(),
                sizeBytes = size,
            )
        }
    }

    /** The URI record's first byte abbreviates a common prefix. */
    private fun decodeUri(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrElse(prefixCode) { "" }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    /** Text records carry a status byte, then an IANA language code, then the text. */
    private fun decodeText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        val charset: Charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
        val offset = 1 + languageLength
        if (offset >= payload.size) return ""
        return String(payload, offset, payload.size - offset, charset)
    }

    private const val GET_VERSION: Byte = 0x60

    private val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
        "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
        "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
        "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
        "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )
}
