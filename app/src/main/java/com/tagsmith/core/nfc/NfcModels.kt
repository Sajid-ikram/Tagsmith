package com.tagsmith.core.nfc

/** What the app knows about a chip family, and how much NDEF it holds. */
enum class ChipType(val label: String, val nominalCapacity: Int) {
    NTAG213("NTAG213", 144),
    NTAG215("NTAG215", 504),
    NTAG216("NTAG216", 888),
    ULTRALIGHT("Mifare Ultralight", 48),
    ULTRALIGHT_EV1_48("Mifare Ultralight EV1", 48),
    ULTRALIGHT_EV1_128("Mifare Ultralight EV1", 128),
    MIFARE_CLASSIC_1K("Mifare Classic 1K", 716),
    MIFARE_CLASSIC_4K("Mifare Classic 4K", 3356),
    DESFIRE("DESFire", 0),
    UNKNOWN("Unknown chip", 0),
}

enum class RecordKind {
    URI, TEXT, CONTACT, WIFI, PHONE, SMS, EMAIL, LOCATION,
    MIME, EXTERNAL, ANDROID_APP, SMART_POSTER, EMPTY, UNKNOWN,
}

/** One decoded NDEF record, ready to render. */
data class NdefRecordView(
    val kind: RecordKind,
    /** e.g. `URI · WELL-KNOWN` — the mono kicker above the value. */
    val typeLabel: String,
    /** The decoded, human-readable value. */
    val display: String,
    /** Set when [display] should render as a tappable link. */
    val link: String? = null,
    val rawHex: String,
    val sizeBytes: Int,
    /** Labelled fields for records that render as a card — a contact, a network. */
    val details: List<Pair<String, String>> = emptyList(),
)

/** Everything a single read yields. Immutable — a snapshot of one tap. */
data class TagSnapshot(
    val uid: String,
    val chip: ChipType,
    /** The chip label actually reported, which may be more specific than [chip]. */
    val chipLabel: String,
    val technologies: List<String>,
    val capacityBytes: Int,
    val usedBytes: Int,
    val writable: Boolean,
    val locked: Boolean,
    val canMakeReadOnly: Boolean,
    val formatted: Boolean,
    val formattable: Boolean,
    val supported: Boolean,
    val records: List<NdefRecordView>,
    val rawHex: String,
    val readAt: Long,
) {
    val freeBytes: Int get() = (capacityBytes - usedBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (capacityBytes <= 0) 0f else (usedBytes.toFloat() / capacityBytes).coerceIn(0f, 1f)
    val isBlank: Boolean get() = records.isEmpty()

    /** The single line that stands in for the tag's contents in a list. */
    val summary: String
        get() = when {
            records.isEmpty() && !formatted -> "Unformatted"
            records.isEmpty() -> "Blank"
            else -> records.first().display
        }
}

/** Whether this phone can do the job at all. */
enum class NfcAvailability { ABSENT, DISABLED, READY }
