package com.tagsmith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * The board's field: a flush-left mono kicker, then a 2px ruled box. Data
 * fields set in mono so a URL or an SSID reads exactly as it will be written.
 */
@Composable
fun LabeledField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    secret: Boolean = false,
    error: String? = null,
    helper: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Tagsmith.colors
    var revealed by remember { mutableStateOf(false) }
    val style = if (mono) TagsmithType.Data.copy(fontSize = TagsmithType.RowTitle.fontSize) else TagsmithType.Body
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Kicker(label)
        Row(
            Modifier
                .fillMaxWidth()
                .border(2.dp, if (error != null) colors.danger else colors.rule, RectangleShape)
                .heightIn(min = 54.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = style, color = colors.inkFainter)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = LocalTextStyle.current.merge(style).copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else minLines,
                    visualTransformation = if (secret && !revealed) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret) KeyboardType.Password else keyboardType,
                        capitalization = capitalization,
                        imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (secret) {
                BarIcon(
                    icon = if (revealed) TagsmithIcons.EyeOff else TagsmithIcons.Eye,
                    contentDescription = if (revealed) "Hide" else "Show",
                    onClick = { revealed = !revealed },
                    tint = colors.inkFaint,
                    iconSize = 20.dp,
                )
            }
            trailing?.invoke()
        }
        when {
            error != null -> Text(error, style = TagsmithType.RowMeta, color = colors.danger)
            helper != null -> Text(helper, style = TagsmithType.RowMeta, color = colors.inkFaint)
        }
    }
}

/** The 2px search box from the templates and inventory screens. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    Row(
        modifier
            .fillMaxWidth()
            .border(2.dp, colors.rule, RectangleShape)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TagsmithIcons.Search, null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = TagsmithType.BodySmall, color = colors.inkFaint)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(TagsmithType.BodySmall).copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            BarIcon(TagsmithIcons.Close, "Clear search", { onValueChange("") }, tint = colors.inkFaint, iconSize = 18.dp)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** One 2px box, hard divisions, the selected cell inverted. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    Row(
        modifier
            .fillMaxWidth()
            .border(2.dp, colors.rule, RectangleShape),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selected) colors.ink else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (selected) TagsmithType.ChipSelected else TagsmithType.Chip,
                    color = if (selected) colors.ground else colors.ink,
                )
            }
        }
    }
}

/** − 20 + in 52dp cells: big enough to hit with a thumb while holding a stack of cards. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..999,
) {
    val colors = Tagsmith.colors
    Row(
        modifier
            .height(52.dp)
            .border(2.dp, colors.rule, RectangleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(TagsmithIcons.Minus, "Fewer", enabled = value > range.first) {
            onValueChange((value - 1).coerceIn(range))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
        Text(
            text = value.toString(),
            style = TagsmithType.Stat,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(76.dp),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
        StepperButton(TagsmithIcons.Plus, "More", enabled = value < range.last) {
            onValueChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Tagsmith.colors
    Box(
        Modifier
            .size(52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) colors.ink else colors.inkFainter,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The small 1px chip under a field: "Paste", "Clear", "Review link builder". */
@Composable
fun MiniChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false,
) {
    val colors = Tagsmith.colors
    val tint = when {
        danger -> colors.danger
        accent -> colors.accent
        else -> colors.ink
    }
    val border = when {
        danger -> colors.dangerBorder
        accent -> colors.accent
        else -> colors.border
    }
    Box(
        modifier
            .border(1.dp, border, RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(label, style = TagsmithType.ChipSmall, color = tint)
    }
}

/**
 * Equal cells over a 2px rule; the selected one is accent with a 3px underline.
 * The client detail's Tags / Batches / Templates / Notes.
 */
@Composable
fun TabStrip(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = if (selected) TagsmithType.ChipSelected else TagsmithType.Chip,
                        color = if (selected) colors.accent else colors.inkFaint,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) colors.accent else Color.Transparent)
                    )
                }
            }
        }
        StrongRule()
    }
}
