package com.tagsmith.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tagsmith.core.data.Client
import com.tagsmith.ui.theme.AvatarFern
import com.tagsmith.ui.theme.AvatarKiln
import com.tagsmith.ui.theme.AvatarOak
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colours a client can be given. All dark enough to carry paper-coloured
 * text in a header, and all from the same wood-and-earth family as the app.
 */
val ClientPalette = listOf(
    AvatarOak,
    AvatarFern,
    AvatarKiln,
    Color(0xFF3E5566), // slate
    Color(0xFF5E3A57), // plum
    Color(0xFF8A6418), // ochre
    Color(0xFF4F5B2C), // moss
    Color(0xFF241D17), // ink
)

/** A new client's colour: the next one along, so neighbours in a list differ. */
fun paletteColorFor(index: Int): Color = ClientPalette[Math.floorMod(index, ClientPalette.size)]

/** Loads a logo file off the main thread; null until loaded, or if it is gone. */
@Composable
fun rememberLogo(path: String?): ImageBitmap? {
    val logo by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return logo
}

/** The client's logo if they have one, otherwise their initial on their colour. */
@Composable
fun ClientAvatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    logoPath: String? = null,
    size: Dp = 34.dp,
    /** Paper square with a coloured initial — for use on the client's own colour. */
    inverted: Boolean = false,
) {
    val logo = rememberLogo(logoPath)
    val colors = Tagsmith.colors
    Box(
        modifier
            .size(size)
            .background(if (logo != null) Color.White else if (inverted) colors.ground else color),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(logo, contentDescription = "$name logo", contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = TagsmithType.RowTitleSmall.copy(fontSize = (size.value * 0.42f).sp),
                color = if (inverted) color else Color.White,
            )
        }
    }
}

@Composable
fun ClientAvatar(client: Client, modifier: Modifier = Modifier, size: Dp = 34.dp, inverted: Boolean = false) =
    ClientAvatar(
        name = client.name,
        color = Color(client.color),
        modifier = modifier,
        logoPath = client.logoPath,
        size = size,
        inverted = inverted,
    )

/** The dashed square that stands for "no client". */
@Composable
fun NoClientAvatar(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val colors = Tagsmith.colors
    Box(
        modifier
            .size(size)
            .border(1.dp, colors.borderDashed, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("—", style = TagsmithType.RowTitleSmall, color = colors.inkFainter)
    }
}

/** Colour swatches for the client form. Selected carries a check, not a ring. */
@Composable
fun ColorSwatches(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClientPalette.forEach { swatch ->
            val argb = swatch.toArgb()
            Box(
                Modifier
                    .size(44.dp)
                    .background(swatch)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(argb) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (argb == selected) {
                    Icon(TagsmithIcons.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

