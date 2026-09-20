package com.tagsmith.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * Four destinations sharing the width evenly. The bar is a rule and four cells —
 * no pill, no shadow, nothing floating. The Scan button sits above it rather
 * than in it, so the bar needs no gap cut out of the middle.
 */
@Composable
fun TagsmithBottomBar(
    current: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    Column(modifier.fillMaxWidth()) {
        StrongRule()
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.groundBar)
                .navigationBarsPadding(),
        ) {
            Destination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = current == destination.route,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Tagsmith.colors
    val tint = if (selected) colors.accent else colors.inkFaint
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                destination.icon,
                contentDescription = destination.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = destination.label,
                style = if (selected) TagsmithType.NavLabelActive else TagsmithType.NavLabel,
                color = tint,
            )
        }
        // The active destination is marked by a 3px rule, not a pill.
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

/** 72dp of ember with the mark and the word — visible from an arm's length. */
@Composable
fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    Column(
        modifier = modifier
            .size(72.dp)
            .shadow(elevation = 10.dp, shape = RectangleShape, clip = false)
            .background(colors.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            TagsmithIcons.Nfc,
            contentDescription = "Scan a tag",
            tint = colors.accentOn,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text("SCAN", style = TagsmithType.StatusChipSmall, color = colors.accentOn)
    }
}
