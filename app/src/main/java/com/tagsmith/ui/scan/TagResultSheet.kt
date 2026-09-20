package com.tagsmith.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagsmith.core.nfc.RecordKind
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.components.IconSquare
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StatusChip
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithTheme
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.openLink
import com.tagsmith.ui.util.rememberClipboard

/**
 * The result, at peek height. The dark scan ground stays visible above it so
 * the tap still feels like the thing that just happened.
 */
@Composable
fun TagResultSheet(
    snapshot: TagSnapshot,
    onExpand: () -> Unit,
    onScanAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkColors = Tagsmith.colors
    Column(modifier.fillMaxSize()) {
        // The scan ground, dimmed — tapping it scans the next tag.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onScanAgain,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(110.dp)
                    .border(2.dp, darkColors.border.copy(alpha = 0.5f), CircleShape)
            )
        }

        // The sheet itself is always light — it is a document, not a moment.
        TagsmithTheme(darkTheme = false) {
            SheetBody(
                snapshot = snapshot,
                onExpand = onExpand,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SheetBody(
    snapshot: TagSnapshot,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Tagsmith.colors
    val context = LocalContext.current
    val clipboard = rememberClipboard()
    val record = snapshot.records.firstOrNull()
    val status = snapshot.statusLabel()

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(colors.border)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (snapshot.isBlank) "Unknown tag" else snapshot.chipLabel,
                    style = TagsmithType.SheetTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(snapshot.uid, style = TagsmithType.DataSmall, color = colors.inkFaint)
            }
            Spacer(Modifier.width(12.dp))
            StatusChip(status.label, status.fill, status.content)
        }

        Column(Modifier.fillMaxWidth()) {
            StrongRule()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).background(colors.accentTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = record.iconFor(),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = record?.typeLabel?.substringBefore(" ·") ?: "No NDEF record",
                        style = TagsmithType.RowTitleSmall,
                        color = colors.ink,
                    )
                    Text(
                        text = record?.display ?: if (snapshot.formatted) {
                            "This tag is formatted but empty."
                        } else {
                            "This tag has no NDEF message yet."
                        },
                        style = TagsmithType.DataSmall,
                        color = colors.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Hairline(color = colors.hairlineWarm)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryAction(
                label = if (record?.link != null) "Open link" else "Full details",
                onClick = { record?.link?.let { openLink(context, it) } ?: onExpand() },
                height = 52.dp,
                loud = false,
                modifier = Modifier.weight(1f),
            )
            IconSquare(
                icon = TagsmithIcons.Copy,
                contentDescription = "Copy value",
                onClick = {
                    clipboard.copy(record?.display ?: snapshot.uid)
                },
            )
            IconSquare(
                icon = TagsmithIcons.Close,
                contentDescription = "Dismiss",
                onClick = onClose,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TagsmithIcons.ChevronUp,
                contentDescription = null,
                tint = colors.inkFaint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Full details", style = TagsmithType.ChipSelected, color = colors.inkFaint)
        }
    }
}

@Composable
internal fun com.tagsmith.core.nfc.NdefRecordView?.iconFor() = when (this?.kind) {
    RecordKind.URI -> TagsmithIcons.Link
    RecordKind.TEXT -> TagsmithIcons.TextRecord
    RecordKind.ANDROID_APP -> TagsmithIcons.Tag
    null -> TagsmithIcons.Nfc
    else -> TagsmithIcons.Tag
}

/** What the header chip says for a tag the app has only just met. */
@Composable
internal fun TagSnapshot.statusLabel(): com.tagsmith.ui.components.StatusVisual {
    val colors = Tagsmith.colors
    return when {
        locked -> com.tagsmith.ui.components.StatusVisual("Locked", colors.lockedTint, colors.locked)
        !formatted -> com.tagsmith.ui.components.StatusVisual("Unformatted", colors.neutralTint, colors.inkMuted)
        isBlank -> com.tagsmith.ui.components.StatusVisual("Blank", colors.neutralTint, colors.inkMuted)
        else -> com.tagsmith.ui.components.StatusVisual("Written", colors.successTint, colors.success)
    }
}
