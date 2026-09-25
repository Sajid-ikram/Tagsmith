package com.tagsmith.ui.payload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tagsmith.core.nfc.ChipType
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.normalizeUrl
import com.tagsmith.core.nfc.PayloadType
import com.tagsmith.core.nfc.RecordKind
import com.tagsmith.ui.components.ByteMeter
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

fun PayloadType.icon(): ImageVector = when (this) {
    PayloadType.URL -> TagsmithIcons.Link
    PayloadType.TEXT -> TagsmithIcons.TextRecord
    PayloadType.CONTACT -> TagsmithIcons.Contact
    PayloadType.WIFI -> TagsmithIcons.Wifi
    PayloadType.TEL -> TagsmithIcons.Phone
    PayloadType.SMS -> TagsmithIcons.Sms
    PayloadType.EMAIL -> TagsmithIcons.Mail
    PayloadType.LOCATION -> TagsmithIcons.Location
    PayloadType.APP -> TagsmithIcons.App
    PayloadType.RAW -> TagsmithIcons.Code
}

fun RecordKind.icon(): ImageVector = when (this) {
    RecordKind.URI -> TagsmithIcons.Link
    RecordKind.TEXT -> TagsmithIcons.TextRecord
    RecordKind.CONTACT -> TagsmithIcons.Contact
    RecordKind.WIFI -> TagsmithIcons.Wifi
    RecordKind.PHONE -> TagsmithIcons.Phone
    RecordKind.SMS -> TagsmithIcons.Sms
    RecordKind.EMAIL -> TagsmithIcons.Mail
    RecordKind.LOCATION -> TagsmithIcons.Location
    RecordKind.ANDROID_APP -> TagsmithIcons.App
    RecordKind.MIME, RecordKind.EXTERNAL, RecordKind.UNKNOWN -> TagsmithIcons.Code
    RecordKind.SMART_POSTER -> TagsmithIcons.Link
    RecordKind.EMPTY -> TagsmithIcons.Nfc
}

/** The byte meter is drawn against the smallest chip in stock. */
val SMALLEST_STOCKED = ChipType.NTAG213.nominalCapacity
val LARGEST_STOCKED = ChipType.NTAG216.nominalCapacity

/** Which of the chips you stock this payload will actually fit on. */
fun fitNote(bytes: Int): String = when {
    bytes == 0 -> "nothing to write yet"
    bytes <= ChipType.NTAG213.nominalCapacity -> "fits ${ChipType.NTAG213.label}"
    bytes <= ChipType.NTAG215.nominalCapacity -> "needs ${ChipType.NTAG215.label} or larger"
    bytes <= ChipType.NTAG216.nominalCapacity -> "needs ${ChipType.NTAG216.label}"
    else -> "too large for any chip you stock"
}

/**
 * "38 of 144 bytes", what it fits, and the bar — amber near the limit, red past
 * it. The number is the real NDEF encoding, not the length of the string typed.
 */
@Composable
fun ByteCounter(bytes: Int, modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    val fits = bytes in 1..SMALLEST_STOCKED
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$bytes of $SMALLEST_STOCKED bytes", style = TagsmithType.DataSmall, color = colors.inkMuted)
            Text(
                fitNote(bytes),
                style = TagsmithType.DataSmall,
                color = when {
                    fits -> colors.success
                    bytes > LARGEST_STOCKED -> colors.danger
                    else -> colors.inkMuted
                },
            )
        }
        ByteMeter(used = bytes, capacity = SMALLEST_STOCKED)
        if (bytes > SMALLEST_STOCKED && bytes <= LARGEST_STOCKED) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Bigger than an ${ChipType.NTAG213.label} — check the stock you're writing to.",
                style = TagsmithType.RowMeta,
                color = colors.inkFaint,
            )
        }
    }
}

/** A name to offer when the operator saves a template without typing one. */
fun NdefPayload.suggestedName(): String = when (this) {
    is NdefPayload.Url -> normalizeUrl(url).let { u ->
        if (u.contains("writereview") || u.contains("g.page/r/")) {
            "Google review link"
        } else {
            u.toUri().host?.removePrefix("www.") ?: "Link"
        }
    }
    is NdefPayload.Text -> text.lineSequence().firstOrNull().orEmpty().take(28).trim().ifEmpty { "Text" }
    is NdefPayload.Contact -> name.ifBlank { company }.ifBlank { "Contact card" }
    is NdefPayload.Wifi -> "Wi-Fi · $ssid"
    is NdefPayload.Phone -> "Call ${number.trim()}"
    is NdefPayload.Sms -> "Text ${number.trim()}"
    is NdefPayload.Email -> subject.ifBlank { "Email ${address.trim()}" }
    is NdefPayload.Location -> if (byAddress) address.lineSequence().first().take(28) else "Map pin"
    is NdefPayload.App -> "Open ${label.ifBlank { packageName }}"
    is NdefPayload.Raw -> mimeType.ifBlank { "Raw record" }
}
