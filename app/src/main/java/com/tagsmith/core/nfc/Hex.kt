package com.tagsmith.core.nfc

private const val HEX = "0123456789ABCDEF"

/** `04:A2:9C:6B:23:80:00` — the form a UID is read aloud in. */
fun ByteArray.toUidString(): String = buildString(size * 3) {
    this@toUidString.forEachIndexed { i, b ->
        if (i > 0) append(':')
        val v = b.toInt() and 0xFF
        append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
}

/** `04 A2 9C 6B` — the form a payload dump is read in. */
fun ByteArray.toHexDump(): String = buildString(size * 3) {
    this@toHexDump.forEachIndexed { i, b ->
        if (i > 0) append(' ')
        val v = b.toInt() and 0xFF
        append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
}

/** Shortens a UID for list rows: `04:A2:9C…80:00`. */
fun String.shortUid(): String {
    val parts = split(':')
    if (parts.size <= 5) return this
    return parts.take(3).joinToString(":") + "…" + parts.takeLast(2).joinToString(":")
}
