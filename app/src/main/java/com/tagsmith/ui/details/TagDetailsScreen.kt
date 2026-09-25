package com.tagsmith.ui.details

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.data.Batch
import com.tagsmith.core.data.HistoryEntry
import com.tagsmith.core.data.TagRecord
import com.tagsmith.core.nfc.OperationResult
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ClientPickerSheet
import com.tagsmith.ui.components.ByteMeter
import com.tagsmith.ui.components.DataRow
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.OutlineChip
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.StatusChip
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.statusVisual
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.scan.iconFor
import com.tagsmith.ui.scan.statusLabel
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.currentLocale
import com.tagsmith.ui.util.openLink
import com.tagsmith.ui.util.rememberClipboard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The expanded sheet: identity, contents, provenance, then the actions.
 * Everything the operator needs to answer "what is this card?".
 */
@Composable
fun TagDetailsScreen(
    snapshot: TagSnapshot,
    onBack: () -> Unit,
    onOverwrite: () -> Unit,
    onErase: () -> Unit,
    onLock: () -> Unit,
    onOpenBatch: (Long) -> Unit,
    onNewClient: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val known by remember(snapshot.uid) { container.ledger.tag(snapshot.uid) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lastWrite by remember(snapshot.uid) { container.ledger.lastWrite(snapshot.uid) }
        .collectAsStateWithLifecycle(initialValue = null)
    val clients by remember { container.clients.all() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val batch by remember(known?.batchId) {
        known?.batchId?.let { container.batches.batch(it) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    var pickingClient by remember { mutableStateOf(false) }

    val colors = Tagsmith.colors
    val context = LocalContext.current
    val clipboard = rememberClipboard()
    var showRawHex by remember { mutableStateOf(false) }
    val status = snapshot.statusLabel()
    val primaryLink = snapshot.records.firstOrNull { it.link != null }?.link

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(TagsmithIcons.Back, "Back", onBack)
            Text(
                "Tag details",
                style = TagsmithType.RowTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            BarIcon(
                icon = TagsmithIcons.Copy,
                contentDescription = "Copy UID",
                onClick = { clipboard.copy(snapshot.uid) },
                iconSize = 20.dp,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = known?.nickname ?: if (snapshot.isBlank) "Unknown tag" else snapshot.chipLabel,
                    style = TagsmithType.HeroSmall,
                    color = colors.ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                StatusChip(status.label, status.fill, status.content)
            }

            // — identity —
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                StrongRule()
                Spacer(Modifier.height(10.dp))
                Kicker("Identity")
                Spacer(Modifier.height(6.dp))
                DataRow("UID", snapshot.uid)
                DataRow("Chip", snapshot.chipLabel)
                DataRow("Tech", snapshot.technologies.joinToString(" · "))

                Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${snapshot.usedBytes} / ${snapshot.capacityBytes} bytes used",
                            style = TagsmithType.DataSmall,
                            color = colors.inkMuted,
                        )
                        Text(
                            "${snapshot.freeBytes} free",
                            style = TagsmithType.DataSmall,
                            color = colors.inkMuted,
                        )
                    }
                    ByteMeter(used = snapshot.usedBytes, capacity = snapshot.capacityBytes)
                }

                Row(
                    Modifier.padding(top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (snapshot.writable) {
                        OutlineChip("Writable", borderColor = colors.success, contentColor = colors.success)
                    } else {
                        OutlineChip("Read-only", borderColor = colors.dangerBorder, contentColor = colors.danger)
                    }
                    OutlineChip(if (snapshot.locked) "Locked" else "Not locked")
                }
            }

            // — contents —
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                StrongRule()
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Kicker(
                        when (snapshot.records.size) {
                            0 -> "Contents · empty"
                            1 -> "Contents · 1 NDEF record"
                            else -> "Contents · ${snapshot.records.size} NDEF records"
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Raw hex", style = TagsmithType.ChipSmall, color = colors.inkFaint)
                        Spacer(Modifier.width(6.dp))
                        SquareSwitch(
                            checked = showRawHex,
                            onCheckedChange = { showRawHex = it },
                            modifier = Modifier.size(width = 34.dp, height = 18.dp),
                        )
                    }
                }

                if (snapshot.records.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(2.dp, colors.rule, RectangleShape)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (snapshot.formatted) {
                                "Formatted, but nothing is written to it."
                            } else {
                                "This tag has no NDEF message yet. Writing to it will format it first."
                            },
                            style = TagsmithType.BodyTiny,
                            color = colors.inkMuted,
                        )
                    }
                } else {
                    snapshot.records.forEachIndexed { index, record ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .border(2.dp, colors.rule, RectangleShape)
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = record.iconFor(),
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.padding(top = 2.dp).size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.typeLabel, style = TagsmithType.Kicker, color = colors.inkFaint)
                                Spacer(Modifier.height(4.dp))
                                val link = record.link
                                if (link != null) {
                                    Text(
                                        text = record.display,
                                        style = TagsmithType.Data.copy(
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        color = colors.accent,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { openLink(context, link) },
                                        ),
                                    )
                                } else if (record.details.isNotEmpty()) {
                                    // A contact or a network reads as a card, not a string.
                                    Text(record.display, style = TagsmithType.RowTitle, color = colors.ink)
                                } else {
                                    Text(record.display, style = TagsmithType.Data, color = colors.ink)
                                }
                                record.details.forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                        Text(label, style = TagsmithType.BodyTiny, color = colors.inkFaint, modifier = Modifier.width(76.dp))
                                        Text(value, style = TagsmithType.Data, color = colors.ink)
                                    }
                                }
                                if (showRawHex && record.rawHex.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.fillMaxWidth().background(colors.groundSubtle).padding(8.dp)) {
                                        Text(
                                            record.rawHex,
                                            style = TagsmithType.DataTiny,
                                            color = colors.inkMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // — provenance, once the app has met this tag before —
            known?.let { record ->
                ProvenanceBlock(
                    record = record,
                    clientName = clients.firstOrNull { it.id == record.clientId }?.name,
                    batch = batch,
                    lastWrite = lastWrite,
                    onOpenBatch = onOpenBatch,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // — actions, in the bottom third —
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.ground)
                .navigationBarsPadding(),
        ) {
            StrongRule()
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip("Overwrite", enabled = snapshot.writable, onClick = onOverwrite)
                    // Still live on a locked tag: who it belongs to is not written on it.
                    ActionChip("Assign client", enabled = true, onClick = { pickingClient = true })
                    ActionChip("Erase", enabled = snapshot.writable && snapshot.formatted, onClick = onErase)
                    ActionChip(
                        label = "Lock",
                        enabled = snapshot.writable && snapshot.canMakeReadOnly,
                        onClick = onLock,
                        danger = true,
                    )
                }
                if (primaryLink != null) {
                    PrimaryAction(
                        label = "Open link",
                        onClick = { openLink(context, primaryLink) },
                        trailingIcon = TagsmithIcons.ExternalLink,
                        height = 54.dp,
                    )
                } else {
                    PrimaryAction(
                        label = if (snapshot.writable) "Write to this tag" else "Back to scan",
                        onClick = if (snapshot.writable) onOverwrite else onBack,
                        trailingIcon = TagsmithIcons.ArrowRight,
                        height = 54.dp,
                    )
                }
            }
        }
    }

    if (pickingClient) {
        ClientPickerSheet(
            clients = clients,
            selectedId = known?.clientId,
            onPick = { id ->
                pickingClient = false
                scope.launch {
                    // A tag read for the first time has no ledger row yet; record the read first.
                    if (container.ledger.known(snapshot.uid) == null) {
                        container.ledger.record(OperationResult(TagOperation.Read, snapshot, null))
                    }
                    container.ledger.assignClient(snapshot.uid, id)
                }
            },
            onNewClient = {
                pickingClient = false
                onNewClient()
            },
            onDismiss = { pickingClient = false },
        )
    }
}

/** Which client, which batch, when, and whether it read back true. */
@Composable
private fun ProvenanceBlock(
    record: TagRecord,
    clientName: String?,
    batch: Batch?,
    lastWrite: HistoryEntry?,
    onOpenBatch: (Long) -> Unit,
) {
    val colors = Tagsmith.colors
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
        StrongRule()
        Column(Modifier.fillMaxWidth().background(colors.groundSubtle).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(10.dp))
            Kicker("Provenance")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProvenanceRow("Client", clientName ?: "Unassigned")
                if (batch != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpenBatch(batch.id) },
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Batch", style = TagsmithType.BodyTiny, color = colors.inkFaint)
                        Text(
                            "${batch.code} · ${batch.writtenCount} cards",
                            style = TagsmithType.DataSmall,
                            color = colors.accent,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Status", style = TagsmithType.BodyTiny, color = colors.inkFaint)
                    val visual = statusVisual(record.status)
                    StatusChip(visual.label, visual.fill, visual.content, small = true)
                }
                record.lastWrittenAt?.let {
                    ProvenanceRow("Written", SimpleDateFormat("d MMM yyyy, HH:mm", currentLocale()).format(Date(it)))
                }
                lastWrite?.verified?.let { verified ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Verified", style = TagsmithType.BodyTiny, color = colors.inkFaint)
                        Text(
                            if (verified) "Yes" else "No",
                            style = TagsmithType.RowTitleSmall,
                            color = if (verified) colors.success else colors.danger,
                        )
                    }
                }
                ProvenanceRow("First seen", com.tagsmith.ui.home.shortTime(record.firstSeenAt))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProvenanceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TagsmithType.BodyTiny, color = Tagsmith.colors.inkFaint)
        Text(value, style = TagsmithType.RowTitleSmall, color = Tagsmith.colors.ink)
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = Tagsmith.colors
    val border = when {
        !enabled -> colors.hairline
        danger -> colors.dangerBorder
        else -> colors.border
    }
    val content = when {
        !enabled -> colors.inkFainter
        danger -> colors.danger
        else -> colors.ink
    }
    Box(
        Modifier
            .border(1.dp, border, RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = TagsmithType.ChipSmall, color = content)
    }
}
