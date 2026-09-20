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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.BlinkingDot
import com.tagsmith.ui.components.FailurePane
import com.tagsmith.ui.components.PhoneMark
import com.tagsmith.ui.components.PulsingRings
import com.tagsmith.ui.components.SnapRings
import com.tagsmith.ui.components.SweepBar
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.OnTapGround
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * The hero screen. Full bleed, minimal chrome, on the dark ground whatever the
 * app theme is — the rings need a dark field to radiate into.
 */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onWriteInstead: () -> Unit,
    onOpenDetails: (TagSnapshot) -> Unit,
    onOpenNfcSettings: () -> Unit,
) = OnTapGround {
    val container = LocalAppContainer.current
    val session = container.nfc
    val phase by session.phase.collectAsStateWithLifecycle()
    val availability by session.availability.collectAsStateWithLifecycle()

    // Arm on entry, stand down on exit — the radio never listens by accident.
    DisposableEffect(Unit) {
        session.arm(TagOperation.Read)
        onDispose { session.cancel() }
    }

    // Switching the radio on from the settings deep link should not need a re-entry.
    LaunchedEffect(availability) {
        if (availability == NfcAvailability.READY && session.phase.value == NfcPhase.Idle) {
            session.arm(TagOperation.Read)
        }
    }

    // A result left over from a write or a lock belongs to that screen, not this one.
    val own = phase.takeIf { it.forOperation == TagOperation.Read } ?: NfcPhase.Idle

    val colors = Tagsmith.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        when (own) {
            is NfcPhase.Failed -> ScanFailure(
                failure = own.failure,
                onRetry = { session.retry() },
                onCancel = onClose,
            )

            is NfcPhase.Done -> TagResultSheet(
                snapshot = own.result.snapshot,
                onExpand = { onOpenDetails(own.result.snapshot) },
                onScanAgain = { session.arm(TagOperation.Read) },
                onClose = onClose,
            )

            else -> ScanWaiting(
                phase = own,
                availability = availability,
                onClose = onClose,
                onWriteInstead = onWriteInstead,
                onOpenNfcSettings = onOpenNfcSettings,
            )
        }
    }
}

@Composable
private fun ScanWaiting(
    phase: NfcPhase,
    availability: NfcAvailability,
    onClose: () -> Unit,
    onWriteInstead: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val colors = Tagsmith.colors
    val detected = phase.isEngaged

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(TagsmithIcons.Close, "Close", onClose, tint = colors.ink)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    availability == NfcAvailability.ABSENT -> {
                        Text("NO NFC RADIO", style = TagsmithType.KickerLoud, color = colors.danger)
                    }

                    availability == NfcAvailability.DISABLED -> {
                        Text("NFC OFF", style = TagsmithType.KickerLoud, color = colors.danger)
                    }

                    detected -> {
                        Text("TAG DETECTED", style = TagsmithType.KickerLoud, color = colors.success)
                    }

                    else -> {
                        BlinkingDot(color = colors.accentLight)
                        Spacer(Modifier.width(8.dp))
                        Text("NFC READY", style = TagsmithType.KickerLoud, color = colors.accentLight)
                    }
                }
            }
            Spacer(Modifier.width(44.dp))
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                if (detected) {
                    SnapRings()
                } else if (availability == NfcAvailability.READY) {
                    PulsingRings()
                } else {
                    Box(Modifier.size(300.dp).border(2.dp, colors.rule, CircleShape))
                }
                PhoneMark(
                    outline = colors.ink,
                    fill = if (detected) colors.groundSubtle else Color.Transparent,
                    pop = detected,
                )
            }

            Spacer(Modifier.height(44.dp))

            when {
                availability != NfcAvailability.READY -> {
                    Text(
                        text = if (availability == NfcAvailability.ABSENT) {
                            "This phone can't read tags"
                        } else {
                            "Switch NFC on to scan"
                        },
                        style = TagsmithType.Hero,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }

                detected -> {
                    Text("Reading tag…", style = TagsmithType.HeroSmall, color = colors.ink)
                    Spacer(Modifier.height(18.dp))
                    SweepBar(
                        modifier = Modifier.width(220.dp),
                        trackColor = colors.hairline,
                        barColor = colors.accent,
                    )
                }

                else -> {
                    Text(
                        text = "Hold a tag against the back of your phone",
                        style = TagsmithType.Hero,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (availability == NfcAvailability.DISABLED) {
                com.tagsmith.ui.components.PrimaryAction(
                    label = "Open NFC settings",
                    onClick = onOpenNfcSettings,
                    trailingIcon = TagsmithIcons.ExternalLink,
                    height = 56.dp,
                )
            } else if (availability == NfcAvailability.ABSENT) {
                Text(
                    text = "There is no NFC adapter in this device, so nothing can be read " +
                        "or written here. The ledger and history still work.",
                    style = TagsmithType.BodyTiny,
                    color = colors.inkMuted,
                )
                com.tagsmith.ui.components.OutlineAction(
                    label = "Back",
                    onClick = onClose,
                    borderColor = colors.border,
                    contentColor = colors.ink,
                    pressedFill = colors.neutralTint,
                )
            } else if (!detected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietChip("Write instead", onWriteInstead)
                }
                Text(
                    text = "The antenna is near the top of most phones.",
                    style = TagsmithType.RowMeta,
                    color = colors.inkFaint,
                )
            } else {
                Text("Keep the tag still.", style = TagsmithType.BodyTiny, color = colors.inkFaint)
            }
        }
    }
}

@Composable
private fun QuietChip(label: String, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Box(
        Modifier
            .border(1.dp, colors.border, RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = TagsmithType.Chip, color = colors.inkMuted)
    }
}

@Composable
private fun ScanFailure(
    failure: NfcFailure,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Tagsmith.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(TagsmithIcons.Close, "Close", onCancel, tint = colors.ink)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                Text(failure.kicker, style = TagsmithType.KickerLoud, color = colors.danger)
            }
            Spacer(Modifier.width(44.dp))
        }
        FailurePane(
            kicker = "",
            headline = failure.headline,
            detail = failure.detail,
            primaryLabel = if (failure.recoverable) "Try again" else null,
            onPrimary = if (failure.recoverable) onRetry else null,
            secondaryLabel = "Cancel",
            onSecondary = onCancel,
            accent = colors.danger,
            ringColor = colors.border,
            modifier = Modifier.weight(1f),
        )
    }
}
