package com.tagsmith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.Template
import com.tagsmith.ui.payload.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/**
 * The bottom sheet, squared off. Material's rounded top would be the only
 * rounded corner in the app, so it is not used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsmithSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Tagsmith.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.ground,
        contentColor = colors.ink,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.border)
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = TagsmithType.SheetTitle,
                    color = colors.ink,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            content()
        }
    }
}

/** One row of an action sheet. Danger rows are red; everything is 56dp tall. */
@Composable
fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
    detail: String? = null,
) {
    val colors = Tagsmith.colors
    val tint = if (danger) colors.danger else colors.ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = TagsmithType.RowTitle, color = tint)
            if (detail != null) Text(detail, style = TagsmithType.RowMeta, color = colors.inkFaint)
        }
    }
}

/**
 * Who a write, template or batch is for. "No client" is always first, so taking
 * a client off is as easy as putting one on.
 */
@Composable
fun ClientPickerSheet(
    clients: List<Client>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onNewClient: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = Tagsmith.colors
    TagsmithSheet(onDismiss = onDismiss, title = "Assign to client") {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            item {
                PickerRow(
                    selected = selectedId == null,
                    onClick = { onPick(null) },
                    leading = { NoClientAvatar() },
                    title = "No client",
                    subtitle = "Stock, or not decided yet",
                )
            }
            items(clients, key = { it.id }) { client ->
                PickerRow(
                    selected = client.id == selectedId,
                    onClick = { onPick(client.id) },
                    leading = { ClientAvatar(client) },
                    title = client.name,
                    subtitle = client.address.ifBlank { null },
                )
            }
        }
        if (onNewClient != null) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                OutlineAction(label = "New client", onClick = onNewClient, trailingIcon = TagsmithIcons.Plus)
            }
        }
        if (clients.isEmpty()) {
            Text(
                "No clients yet. Add one and every write for them lands on their record.",
                style = TagsmithType.BodyTiny,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Load a saved payload. The list is the templates screen's, minus the editing. */
@Composable
fun TemplatePickerSheet(
    templates: List<Template>,
    onPick: (Template) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Tagsmith.colors
    TagsmithSheet(onDismiss = onDismiss, title = "Templates") {
        if (templates.isEmpty()) {
            Text(
                "Nothing saved yet. Turn on \"Save as template\" when you write, or build one from the templates screen.",
                style = TagsmithType.BodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(templates, key = { it.id }) { template ->
                PickerRow(
                    selected = false,
                    onClick = { onPick(template) },
                    leading = {
                        IconWell(fill = if (template.favourite) colors.accentTint else colors.neutralTint, size = 38.dp) {
                            Icon(
                                template.payloadType.icon(),
                                null,
                                tint = if (template.favourite) colors.accent else colors.ink,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    },
                    title = template.name,
                    subtitle = template.preview,
                    monoSubtitle = true,
                )
            }
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlineAction(label = "Manage templates", onClick = onManage, trailingIcon = TagsmithIcons.ArrowRight)
        }
    }
}

@Composable
private fun PickerRow(
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    monoSubtitle: Boolean = false,
) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentTint else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = TagsmithType.RowTitle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = if (monoSubtitle) TagsmithType.DataTiny else TagsmithType.RowMeta,
                    color = colors.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) Icon(TagsmithIcons.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

/** The row on a form that opens the client picker: label left, client right. */
@Composable
fun ClientSelectorRow(
    client: Client?,
    onClick: () -> Unit,
    label: String = "Assign to client",
    divider: Boolean = false,
) {
    val colors = Tagsmith.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = TagsmithType.BodySmall,
                color = colors.inkFaint,
                modifier = Modifier.weight(1f),
            )
            if (client != null) {
                ClientAvatar(client, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(client.name, style = TagsmithType.RowTitleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("None", style = TagsmithType.RowTitleSmall, color = colors.inkFaint)
            }
            Spacer(Modifier.width(6.dp))
            Icon(TagsmithIcons.ChevronDown, null, tint = colors.inkFaint, modifier = Modifier.size(16.dp))
        }
        if (divider) Hairline()
    }
}
