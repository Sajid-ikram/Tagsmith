package com.tagsmith.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Template
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.EmptyState
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.IconWell
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.ScreenTitle
import com.tagsmith.ui.components.SearchField
import com.tagsmith.ui.components.SheetAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TagsmithSheet
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.payload.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val container: AppContainer) : ViewModel() {
    val templates: StateFlow<List<Template>?> = container.templates.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun duplicate(t: Template) = viewModelScope.launch { container.templates.duplicate(t) }
    fun delete(t: Template) = viewModelScope.launch { container.templates.delete(t) }
    fun toggleFavourite(t: Template) = viewModelScope.launch { container.templates.setFavourite(t.id, !t.favourite) }
    fun move(t: Template, by: Int) = viewModelScope.launch { container.templates.move(t.id, by) }
}

data class TemplatesNav(
    val onBack: () -> Unit,
    val onNew: () -> Unit,
    val onEdit: (Long) -> Unit,
    val onWrite: (Long) -> Unit,
    val onBatch: (Long) -> Unit,
    val onReviewBuilder: () -> Unit,
)

/**
 * Saved payloads: type icon, name, a mono preview of what goes on the tag, and
 * how often it has been written. Favourites carry the ember well.
 */
@Composable
fun TemplatesScreen(nav: TemplatesNav) {
    val viewModel: TemplatesViewModel = containerViewModel { TemplatesViewModel(it) }
    val all by viewModel.templates.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    var query by remember { mutableStateOf("") }
    var reordering by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<Template?>(null) }
    var confirmingDelete by remember { mutableStateOf<Template?>(null) }

    val templates = all.orEmpty().filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.preview.contains(query, ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(TagsmithIcons.Back, "Back", nav.onBack)
            Spacer(Modifier.weight(1f))
            if ((all?.size ?: 0) > 1) {
                TextAction(if (reordering) "Done" else "Reorder", onClick = { reordering = !reordering })
            }
        }
        ScreenTitle("Templates", modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))

        if (all?.isEmpty() == true) {
            Column(Modifier.weight(1f)) {
                EmptyState(
                    title = "Nothing saved yet",
                    body = "Save a payload you write often — a review link, the café Wi-Fi — and it's one tap away from Home.",
                    icon = TagsmithIcons.Templates,
                    actionLabel = "Build a review link",
                    onAction = nav.onReviewBuilder,
                    quietAction = true,
                )
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!reordering) {
                    SearchField(query, { query = it }, placeholder = "Search templates")
                    OutlineAction(
                        label = "Google review link builder",
                        onClick = nav.onReviewBuilder,
                        trailingIcon = TagsmithIcons.Review,
                        height = 48.dp,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            ) {
                itemsIndexed(templates, key = { _, t -> t.id }) { index, template ->
                    if (index == 0) StrongRule() else Hairline()
                    TemplateRow(
                        template = template,
                        reordering = reordering,
                        canMoveUp = index > 0,
                        canMoveDown = index < templates.lastIndex,
                        onClick = { acting = template },
                        onMove = { viewModel.move(template, it) },
                    )
                }
                if (templates.isEmpty() && query.isNotBlank()) {
                    item {
                        Text(
                            "No template matches \"$query\".",
                            style = TagsmithType.BodySmall,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }

        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp)) {
            PrimaryAction(label = "New template", onClick = nav.onNew, trailingIcon = TagsmithIcons.Plus, height = 56.dp)
        }
    }

    acting?.let { template ->
        TagsmithSheet(onDismiss = { acting = null }, title = template.name) {
            SheetAction("Write this", TagsmithIcons.Nfc, { acting = null; nav.onWrite(template.id) }, detail = template.preview)
            SheetAction("Start a batch", TagsmithIcons.Batch, { acting = null; nav.onBatch(template.id) })
            SheetAction("Edit", TagsmithIcons.Edit, { acting = null; nav.onEdit(template.id) })
            SheetAction("Duplicate", TagsmithIcons.Duplicate, { acting = null; viewModel.duplicate(template) })
            SheetAction(
                if (template.favourite) "Remove from favourites" else "Add to favourites",
                if (template.favourite) TagsmithIcons.StarOutline else TagsmithIcons.Star,
                { acting = null; viewModel.toggleFavourite(template) },
            )
            SheetAction("Delete", TagsmithIcons.Trash, { acting = null; confirmingDelete = template }, danger = true)
        }
    }

    confirmingDelete?.let { template ->
        TagsmithSheet(onDismiss = { confirmingDelete = null }, title = "Delete “${template.name}”?") {
            Text(
                "Tags already written keep what's on them, and finished batches keep their own copy. Only the template goes.",
                style = TagsmithType.BodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction(label = "Keep it", onClick = { confirmingDelete = null })
                PrimaryAction(
                    label = "Delete template",
                    onClick = {
                        viewModel.delete(template)
                        confirmingDelete = null
                    },
                    background = colors.danger,
                    height = 52.dp,
                    loud = false,
                )
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: Template,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !reordering,
                onClick = onClick,
            )
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(fill = if (template.favourite) colors.accentTint else colors.neutralTint, size = 38.dp) {
            Icon(
                template.payloadType.icon(),
                null,
                tint = if (template.favourite) colors.accent else colors.ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.name,
                    style = TagsmithType.RowTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (template.favourite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(TagsmithIcons.Star, contentDescription = "Favourite", tint = colors.accent, modifier = Modifier.size(13.dp))
                }
            }
            Text(template.preview, style = TagsmithType.DataTiny, color = colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        if (reordering) {
            BarIcon(TagsmithIcons.ChevronUp, "Move up", { if (canMoveUp) onMove(-1) }, tint = if (canMoveUp) colors.ink else colors.inkFainter)
            BarIcon(TagsmithIcons.ChevronDown, "Move down", { if (canMoveDown) onMove(1) }, tint = if (canMoveDown) colors.ink else colors.inkFainter)
        } else {
            Text("${template.useCount}×", style = TagsmithType.DataTiny, color = colors.inkFaint)
        }
    }
}
