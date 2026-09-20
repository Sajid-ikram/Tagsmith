package com.tagsmith.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/** The 2px rule that opens a section. Alignment and rules do all the organising. */
@Composable
fun StrongRule(modifier: Modifier = Modifier, color: Color = Tagsmith.colors.rule) {
    Box(modifier.fillMaxWidth().height(2.dp).background(color))
}

/** The hairline between rows inside a section. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Tagsmith.colors.hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A flush-left mono label in caps — the grammar borrowed from Modernist. */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Tagsmith.colors.inkFaint,
) {
    Text(
        text = text.uppercase(),
        style = TagsmithType.Kicker,
        color = color,
        modifier = modifier,
    )
}

/** `UID   04:A2:9C:6B:23:80:00` — label left in muted ink, value right in mono. */
@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Tagsmith.colors.ink,
    mono: Boolean = true,
    divider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(label, style = TagsmithType.BodyTiny, color = Tagsmith.colors.inkFaint)
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                style = if (mono) TagsmithType.Data else TagsmithType.BodyTiny,
                color = valueColor,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (divider) Hairline()
    }
}

/** A row with a label on the left and any control on the right. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    control: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = TagsmithType.Body.copy(fontSize = TagsmithType.RowTitle.fontSize),
                color = Tagsmith.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            control()
        }
        if (divider) Hairline()
    }
}

/** The square switch — a 44×24 track with an 18dp square knob, no rounding. */
@Composable
fun SquareSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val offset by animateFloatAsState(if (checked) 1f else 0f, tween(140), label = "knob")
    val colors = Tagsmith.colors
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .background(if (checked) colors.accent else colors.trackOff)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        val knob = if (checked) Color.White else colors.ground
        Box(
            Modifier
                .padding(3.dp)
                .size(18.dp)
                .align(if (offset > 0.5f) Alignment.CenterEnd else Alignment.CenterStart)
                .background(knob)
        )
    }
}

/** The filter / payload-type chip. Selected is a solid ink fill, not a tint. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accented: Boolean = false,
) {
    val colors = Tagsmith.colors
    // Selected is an inversion, not a tint: ink on ground in light, ground on ink
    // in dark. Filling with the rule colour would bury the label on a dark ground.
    val fill = when {
        selected -> colors.ink
        else -> Color.Transparent
    }
    val border = when {
        selected -> colors.ink
        accented -> colors.accent
        else -> colors.border
    }
    val content = when {
        selected -> colors.ground
        accented -> colors.accent
        enabled -> colors.ink
        else -> colors.inkFainter
    }
    Box(
        modifier = modifier
            .background(fill)
            .border(BorderStroke(1.dp, border), RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = if (selected) TagsmithType.ChipSelected else TagsmithType.Chip,
            color = content,
            maxLines = 1,
        )
    }
}

/** BLANK / WRITTEN / LOCKED / DEPLOYED — mono caps on a tint. */
@Composable
fun StatusChip(
    label: String,
    fill: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(fill)
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 4.dp else 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = if (small) TagsmithType.StatusChipSmall else TagsmithType.StatusChip,
            color = contentColor,
        )
    }
}

/** An outlined state chip: "Writable", "Not locked". */
@Composable
fun OutlineChip(
    label: String,
    modifier: Modifier = Modifier,
    borderColor: Color = Tagsmith.colors.border,
    contentColor: Color = Tagsmith.colors.inkFaint,
) {
    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, borderColor), RectangleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, style = TagsmithType.ChipSmall, color = contentColor)
    }
}

/**
 * The byte counter's bar. Amber near the limit, red past it — the operator
 * should see trouble before they tap the tag, not after.
 */
@Composable
fun ByteMeter(
    used: Int,
    capacity: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val colors = Tagsmith.colors
    val fraction = if (capacity <= 0) 0f else (used.toFloat() / capacity)
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(220), label = "fill")
    val barColor = when {
        capacity > 0 && used > capacity -> colors.danger
        fraction > 0.85f -> Color(0xFFB8860B)
        else -> colors.accent
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(if (colors.isDark) colors.neutralTint else Tagsmith.colors.hairline)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(barColor)
        )
    }
}

/** A neutral square well for an action icon in a list row. */
@Composable
fun IconWell(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    fill: Color = Tagsmith.colors.neutralTint,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(size).background(fill),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

