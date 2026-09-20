package com.tagsmith.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * Every list needs one of these: a line of plain copy and a single action.
 * No illustration — the board asked for restraint here.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(64.dp).background(Tagsmith.colors.neutralTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Tagsmith.colors.inkFaint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
        StrongRule()
        Spacer(Modifier.height(14.dp))
        Text(title, style = TagsmithType.HeroSmall, color = Tagsmith.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(body, style = TagsmithType.Body, color = Tagsmith.colors.inkMuted)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            PrimaryAction(label = actionLabel, onClick = onAction, trailingIcon = TagsmithIcons.ArrowRight)
        }
    }
}

/**
 * The full-bleed failure pane: kicker, broken ring, headline, detail, and the
 * two recovery actions in the bottom third.
 */
@Composable
fun FailurePane(
    kicker: String,
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String = "Cancel",
    onSecondary: (() -> Unit)? = null,
    accent: Color = Tagsmith.colors.danger,
    ringColor: Color = Tagsmith.colors.border,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrokenRing(color = ringColor) {
                Icon(
                    TagsmithIcons.Alert,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(34.dp))
            Column(
                modifier = Modifier.padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (kicker.isNotBlank()) {
                    Text(kicker.uppercase(), style = TagsmithType.KickerLoud, color = accent)
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = headline,
                    style = TagsmithType.HeroSmall,
                    color = Tagsmith.colors.ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = detail,
                    style = TagsmithType.Body.copy(fontSize = TagsmithType.RowTitle.fontSize),
                    color = Tagsmith.colors.inkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (primaryLabel != null && onPrimary != null) {
                PrimaryAction(label = primaryLabel, onClick = onPrimary, height = 56.dp)
            }
            if (onSecondary != null) {
                OutlineAction(
                    label = secondaryLabel,
                    onClick = onSecondary,
                    borderColor = Tagsmith.colors.border,
                    contentColor = Tagsmith.colors.ink,
                    pressedFill = Tagsmith.colors.groundSubtle,
                )
            }
        }
    }
}

/** A hairline-ruled note with an info glyph — "We'll bring you straight back here." */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        StrongRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TagsmithIcons.Alert,
                contentDescription = null,
                tint = Tagsmith.colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = TagsmithType.BodyTiny, color = Tagsmith.colors.inkMuted)
        }
        Hairline(color = Tagsmith.colors.hairlineWarm)
    }
}
