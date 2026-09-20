package com.tagsmith.ui.write

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.ui.components.DrawnCheck
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PhoneMark
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.PulsingRings
import com.tagsmith.ui.components.SnapRings
import com.tagsmith.ui.components.SweepBar
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.OnTapGround
import com.tagsmith.ui.theme.SuccessBorder
import com.tagsmith.ui.theme.SuccessGround
import com.tagsmith.ui.theme.SuccessOn
import com.tagsmith.ui.theme.SuccessOnMuted
import com.tagsmith.ui.theme.SuccessPressed
import com.tagsmith.ui.theme.SuccessRule
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * The tap prompt. The same rings as the scan screen, because it is the same
 * moment — only what happens next differs.
 */
@Composable
fun TapPromptScreen(
    title: String,
    detail: String,
    value: String,
    detected: Boolean,
    onCancel: () -> Unit,
) = OnTapGround {
    val colors = Tagsmith.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (detected) "WRITING" else "READY TO WRITE",
                style = TagsmithType.KickerLoud,
                color = if (detected) colors.success else colors.accentLight,
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                if (detected) SnapRings() else PulsingRings()
                PhoneMark(
                    outline = colors.ink,
                    fill = if (detected) colors.groundSubtle else Color.Transparent,
                    pop = detected,
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(
                text = if (detected) "Writing…" else title,
                style = TagsmithType.Hero,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(detail, style = TagsmithType.BodyTiny, color = colors.inkFainter)
            if (detected) {
                Spacer(Modifier.height(18.dp))
                SweepBar(
                    modifier = Modifier.width(220.dp),
                    trackColor = colors.hairline,
                    barColor = colors.accent,
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("ABOUT TO WRITE", style = TagsmithType.Kicker, color = colors.accentLight)
                Spacer(Modifier.height(4.dp))
                Text(value, style = TagsmithType.Data, color = colors.ink)
            }
            OutlineAction(
                label = "Cancel",
                onClick = onCancel,
                borderColor = colors.border,
                contentColor = colors.ink,
                pressedFill = colors.neutralTint,
            )
        }
    }
}

/**
 * Written and verified. A green ground and a check that draws itself in — the
 * only screen in the app that leaves the workshop palette, and it earns it.
 */
@Composable
fun WriteSuccessScreen(
    value: String,
    chipLabel: String,
    uid: String,
    verified: Boolean?,
    locked: Boolean,
    onWriteAnother: () -> Unit,
    onLock: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SuccessGround)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DrawnCheck(color = com.tagsmith.ui.theme.DarkSuccess)
            Spacer(Modifier.height(30.dp))
            Text(
                text = when (verified) {
                    true -> "Written & verified"
                    false -> "Written"
                    null -> "Written"
                },
                style = TagsmithType.Hero,
                color = SuccessOn,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = listOfNotNull(
                    chipLabel,
                    uid,
                    if (locked) "locked" else null,
                ).joinToString(" · "),
                style = TagsmithType.BodySmall,
                color = SuccessOnMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuccessRule, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("ON THE TAG", style = TagsmithType.Kicker, color = com.tagsmith.ui.theme.DarkSuccess)
                Spacer(Modifier.height(4.dp))
                Text(value, style = TagsmithType.Data, color = SuccessOn)
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                label = "Write another",
                onClick = onWriteAnother,
                background = SuccessOn,
                contentColor = SuccessGround,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!locked) {
                    OutlineAction(
                        label = "Lock this tag",
                        onClick = onLock,
                        borderColor = SuccessBorder,
                        contentColor = SuccessOn,
                        pressedFill = SuccessPressed,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlineAction(
                    label = "Done",
                    onClick = onDone,
                    borderColor = SuccessBorder,
                    contentColor = SuccessOn,
                    pressedFill = SuccessPressed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A failed write. A verification mismatch gets its own treatment: amber, not
 * green, with expected and read-back side by side.
 */
@Composable
fun WriteFailureScreen(
    failure: NfcFailure,
    expected: String,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) = OnTapGround {
    val colors = Tagsmith.colors
    val amber = Color(0xFFE0A33C)
    val isMismatch = failure is NfcFailure.VerificationMismatch
    val accent = if (isMismatch) amber else colors.danger

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(failure.kicker, style = TagsmithType.KickerLoud, color = accent)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(64.dp).border(3.dp, accent, RectangleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = TagsmithIcons.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(failure.headline, style = TagsmithType.Hero, color = colors.ink)
            Spacer(Modifier.height(12.dp))
            Text(failure.detail, style = TagsmithType.Body, color = colors.inkMuted)

            if (isMismatch) {
                Spacer(Modifier.height(22.dp))
                ComparisonBlock(
                    label = "EXPECTED",
                    value = expected,
                    accent = accent,
                    valueColor = colors.ink,
                )
                Spacer(Modifier.height(10.dp))
                ComparisonBlock(
                    label = "READ BACK",
                    value = failure.readBack,
                    accent = accent,
                    valueColor = colors.inkMuted,
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (failure.recoverable) {
                PrimaryAction(
                    label = if (isMismatch) "Rewrite the tag" else "Try again",
                    onClick = onRetry,
                    height = 56.dp,
                )
            }
            OutlineAction(
                label = "Back to the payload",
                onClick = onEdit,
                borderColor = colors.border,
                contentColor = colors.ink,
                pressedFill = colors.neutralTint,
            )
        }
    }
}

@Composable
private fun ComparisonBlock(
    label: String,
    value: String,
    accent: Color,
    valueColor: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.5f), RectangleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = TagsmithType.Kicker, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(value, style = TagsmithType.Data, color = valueColor)
    }
}
