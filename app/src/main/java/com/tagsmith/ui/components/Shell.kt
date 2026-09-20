package com.tagsmith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tagsmith.core.data.TagStatus
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/** The modest bar: a 44dp back target, a 15px title, and at most one action. */
@Composable
fun TagsmithTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIsClose: Boolean = false,
    tint: Color = Tagsmith.colors.ink,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BarIcon(
                icon = if (backIsClose) TagsmithIcons.Close else TagsmithIcons.Back,
                contentDescription = if (backIsClose) "Close" else "Back",
                onClick = onBack,
                tint = tint,
            )
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = TagsmithType.RowTitle,
            color = tint,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 0.dp else 8.dp),
        )
        action?.invoke()
    }
}

/** The big flush-left screen title used by the tab destinations. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = TagsmithType.ScreenTitle, color = Tagsmith.colors.ink)
        trailing?.invoke()
    }
}

/** A tinted strip with a 6dp accent edge — the resume-batch and warning banners. */
@Composable
fun NoticeStrip(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    fill: Color = Tagsmith.colors.accentTint,
    edge: Color = Tagsmith.colors.accent,
    titleColor: Color = Tagsmith.colors.accent,
    detailColor: Color = Tagsmith.colors.inkMuted,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(fill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).size(width = 6.dp, height = 56.dp).background(edge))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = TagsmithType.RowTitleSmall, color = titleColor)
            Text(detail, style = TagsmithType.DataSmall, color = detailColor)
        }
        trailing?.let {
            Box(Modifier.padding(end = 12.dp)) { it() }
        }
    }
}

/** How a status reads in a list: mono caps on its own tint. */
data class StatusVisual(val label: String, val fill: Color, val content: Color)

@Composable
fun statusVisual(status: TagStatus): StatusVisual {
    val colors = Tagsmith.colors
    return when (status) {
        TagStatus.BLANK -> StatusVisual("Blank", colors.neutralTint, colors.inkMuted)
        TagStatus.WRITTEN -> StatusVisual("Written", colors.neutralTint, colors.inkMuted)
        TagStatus.LOCKED -> StatusVisual("Locked", colors.lockedTint, colors.locked)
        TagStatus.DEPLOYED -> StatusVisual("Deployed", colors.successTint, colors.success)
        TagStatus.RETIRED -> StatusVisual("Retired", colors.neutralTint, colors.inkFaint)
        TagStatus.FAULTY -> StatusVisual("Faulty", colors.dangerTint, colors.danger)
    }
}
