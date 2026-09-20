package com.tagsmith.ui.write

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.PayloadType
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ByteMeter
import com.tagsmith.ui.components.ChoiceChip
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.rememberClipboard

/**
 * Compose a payload, then tap. The write itself never leaves this screen —
 * the tap prompt, the success and the failures are all states of it.
 */
@Composable
fun WriteScreen(
    prefill: String?,
    availability: NfcAvailability,
    onClose: () -> Unit,
    onLockTag: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val viewModel: WriteViewModel = containerViewModel(key = "write") {
        WriteViewModel(it, prefill)
    }
    val phase by viewModel.session.phase.collectAsStateWithLifecycle()

    // Only this screen's own writes are drawn here; a scan or a lock elsewhere
    // in the session leaves the form untouched.
    val current = phase.takeIf { it.forOperation is TagOperation.Write } ?: NfcPhase.Idle

    // Leaving the screen must never leave the radio armed.
    DisposableEffect(Unit) { onDispose { viewModel.cancel() } }

    when (current) {
        is NfcPhase.Done -> WriteSuccessScreen(
            value = viewModel.displayValue,
            chipLabel = current.result.snapshot.chipLabel,
            uid = current.result.snapshot.uid,
            verified = current.result.verified,
            locked = current.result.snapshot.locked,
            onWriteAnother = { viewModel.cancel() },
            onLock = onLockTag,
            onDone = {
                viewModel.cancel()
                onClose()
            },
        )

        is NfcPhase.Failed -> WriteFailureScreen(
            failure = current.failure,
            expected = viewModel.displayValue,
            onRetry = { viewModel.retry() },
            onEdit = { viewModel.cancel() },
        )

        is NfcPhase.Waiting, is NfcPhase.Detected, is NfcPhase.Working -> TapPromptScreen(
            title = "Tap the tag to write",
            detail = buildString {
                append(if (viewModel.verifyAfterWrite) "Verify on" else "Verify off")
                append(" · ")
                append(if (viewModel.lockAfterWrite) "auto-lock on" else "auto-lock off")
            },
            value = viewModel.displayValue,
            detected = current.isEngaged,
            onCancel = { viewModel.cancel() },
        )

        NfcPhase.Idle -> ComposeForm(
            viewModel = viewModel,
            availability = availability,
            onClose = onClose,
            onOpenNfcSettings = onOpenNfcSettings,
        )
    }
}

@Composable
private fun ComposeForm(
    viewModel: WriteViewModel,
    availability: NfcAvailability,
    onClose: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val colors = Tagsmith.colors
    val clipboard = rememberClipboard()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(TagsmithIcons.Close, "Close", onClose)
            Text("Write a tag", style = TagsmithType.RowTitle, color = colors.ink, modifier = Modifier.weight(1f))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PayloadType.entries.forEach { type ->
                ChoiceChip(
                    label = type.label,
                    selected = viewModel.payloadType == type,
                    enabled = type.available,
                    onClick = { viewModel.selectType(type) },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (viewModel.payloadType) {
                PayloadType.TEXT -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker("Text to write")
                    MonoField(
                        value = viewModel.text,
                        onValueChange = viewModel::updateText,
                        placeholder = "Anything you like",
                        singleLine = false,
                    )
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker("Destination URL")
                    MonoField(
                        value = viewModel.url,
                        onValueChange = viewModel::updateUrl,
                        placeholder = "https://",
                        keyboardType = KeyboardType.Uri,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChip("Paste") {
                            clipboard.paste(viewModel::updateUrl)
                        }
                        if (viewModel.url.isNotBlank()) {
                            SmallChip("Clear") { viewModel.updateUrl("") }
                        }
                    }
                }
            }

            Column {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${viewModel.byteSize} of ${WriteViewModel.SMALLEST_STOCKED} bytes",
                        style = TagsmithType.DataSmall,
                        color = colors.inkMuted,
                    )
                    Text(
                        viewModel.fitNote,
                        style = TagsmithType.DataSmall,
                        color = if (viewModel.fitsSmallestChip) colors.success else colors.inkMuted,
                    )
                }
                ByteMeter(used = viewModel.byteSize, capacity = WriteViewModel.SMALLEST_STOCKED)
            }

            Column {
                StrongRule()
                Spacer(Modifier.height(10.dp))
                Kicker("Options")
                Spacer(Modifier.height(4.dp))
                SettingRow("Verify after writing") {
                    SquareSwitch(viewModel.verifyAfterWrite, viewModel::updateVerify)
                }
                SettingRow("Lock after writing") {
                    SquareSwitch(viewModel.lockAfterWrite, viewModel::updateLock)
                }
                if (viewModel.lockAfterWrite) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Locking is permanent. Once written, this tag can never be " +
                            "rewritten, erased or reformatted by anyone.",
                        style = TagsmithType.BodyTiny,
                        color = colors.danger,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.ground)
                .navigationBarsPadding(),
        ) {
            StrongRule()
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp)) {
                if (availability == NfcAvailability.READY) {
                    PrimaryAction(
                        label = "Write tag",
                        onClick = viewModel::startWrite,
                        trailingIcon = TagsmithIcons.Nfc,
                        enabled = viewModel.canWrite,
                    )
                } else {
                    OutlineAction(
                        label = if (availability == NfcAvailability.ABSENT) {
                            "This phone has no NFC radio"
                        } else {
                            "Switch NFC on to write"
                        },
                        onClick = onOpenNfcSettings,
                        enabled = availability == NfcAvailability.DISABLED,
                        trailingIcon = TagsmithIcons.ExternalLink,
                        height = 58.dp,
                    )
                }
            }
        }
    }
}

/** The 2px-bordered field. Mono, because what goes on a tag is data. */
@Composable
private fun MonoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = Tagsmith.colors
    Box(
        modifier
            .fillMaxWidth()
            .border(2.dp, colors.rule, RectangleShape)
            .heightIn(min = 54.dp)
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TagsmithType.Data, color = colors.inkFainter)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(TagsmithType.Data).copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Box(
        Modifier
            .border(1.dp, colors.border, RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(label, style = TagsmithType.ChipSmall, color = colors.ink)
    }
}
