package com.tagsmith.ui.clients

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Batch
import com.tagsmith.core.data.BatchStatus
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.TagRecord
import com.tagsmith.core.data.Template
import com.tagsmith.core.nfc.shortUid
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ClientAvatar
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.IconWell
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StatusChip
import com.tagsmith.ui.components.TabStrip
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.components.statusVisual
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.home.shortTime
import com.tagsmith.ui.payload.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.currentLocale
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClientDetailViewModel(private val container: AppContainer, private val clientId: Long) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val client: StateFlow<Client?> = container.clients.client(clientId).state(null)
    val tags: StateFlow<List<TagRecord>> = container.clients.tagsFor(clientId).state(emptyList())
    val batches: StateFlow<List<Batch>> = container.clients.batchesFor(clientId).state(emptyList())
    val templates: StateFlow<List<Template>> = container.clients.templatesFor(clientId).state(emptyList())

    suspend fun saveNotes(notes: String) = container.clients.updateNotes(clientId, notes)
}


data class ClientDetailNav(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onNewBatch: () -> Unit,
    val onOpenBatch: (Batch) -> Unit,
    val onWriteTemplate: (Long) -> Unit,
)

/**
 * A client's page: their colour across the header, then what they have —
 * tags, batches, templates — and notes for the next visit.
 */
@OptIn(FlowPreview::class)
@Composable
fun ClientDetailScreen(clientId: Long, nav: ClientDetailNav) {
    val viewModel: ClientDetailViewModel = containerViewModel(key = "client-$clientId") {
        ClientDetailViewModel(it, clientId)
    }
    val client by viewModel.client.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val current = client ?: run {
        Column(Modifier.fillMaxSize().background(colors.ground).statusBarsPadding()) {
            BarIcon(TagsmithIcons.Back, "Back", nav.onBack, modifier = Modifier.padding(12.dp))
        }
        return
    }
    val brand = Color(current.color)
    // Every palette colour is dark, so the header wants light status-bar icons.
    com.tagsmith.ui.util.StatusBarIcons(lightIcons = true)
    // Every palette colour is dark enough to carry paper-coloured text.
    val onBrand = com.tagsmith.ui.theme.Ground

    Column(Modifier.fillMaxSize().background(colors.ground)) {
        // — the header, in the client's colour —
        Column(
            Modifier
                .fillMaxWidth()
                .background(brand)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BarIcon(TagsmithIcons.Back, "Back", nav.onBack, tint = onBrand)
                Spacer(Modifier.weight(1f))
                TextAction("Edit", onClick = nav.onEdit, color = onBrand)
            }
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ClientAvatar(current, size = 56.dp, inverted = true)
                Spacer(Modifier.height(2.dp))
                Text(current.name, style = TagsmithType.ScreenTitle, color = onBrand)
                Text(
                    listOfNotNull(
                        current.address.takeIf { it.isNotBlank() },
                        "since " + SimpleDateFormat("MMM yyyy", currentLocale()).format(Date(current.createdAt)),
                    ).joinToString(" · "),
                    style = TagsmithType.BodyTiny,
                    color = onBrand.copy(alpha = 0.78f),
                )
                val contact = listOfNotNull(
                    current.contactName.takeIf { it.isNotBlank() },
                    current.phone.takeIf { it.isNotBlank() },
                    current.email.takeIf { it.isNotBlank() },
                    current.website.takeIf { it.isNotBlank() },
                )
                if (contact.isNotEmpty()) {
                    Text(contact.joinToString(" · "), style = TagsmithType.DataSmall, color = onBrand.copy(alpha = 0.78f))
                }
            }
        }

        TabStrip(
            tabs = listOf("Tags ${tags.size}", "Batches ${batches.size}", "Templates ${templates.size}", "Notes"),
            selectedIndex = tab,
            onSelect = { tab = it },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (tab) {
                0 -> if (tags.isEmpty()) {
                    Empty("No tags yet. Write one with this client selected and it lands here.")
                } else {
                    tags.forEach { TagRow(it) }
                }

                1 -> if (batches.isEmpty()) {
                    Empty("No batches yet. Start one to program a run of cards for ${current.name}.")
                } else {
                    batches.forEach { BatchRow(it, onClick = { nav.onOpenBatch(it) }) }
                }

                2 -> if (templates.isEmpty()) {
                    Empty("No templates for this client. Pick them as the client when you save a template.")
                } else {
                    templates.forEach { TemplateRow(it, onClick = { nav.onWriteTemplate(it.id) }) }
                }

                else -> NotesEditor(current.notes, viewModel::saveNotes)
            }
        }

        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                label = "New batch",
                onClick = nav.onNewBatch,
                trailingIcon = TagsmithIcons.Batch,
                height = 54.dp,
                loud = false,
                modifier = Modifier.weight(1f),
            )
            val dial = current.phone.takeIf { it.isNotBlank() }
            val mail = current.email.takeIf { it.isNotBlank() }
            if (dial != null || mail != null) {
                OutlineAction(
                    label = if (dial != null) "Call" else "Email",
                    onClick = {
                        val intent = if (dial != null) {
                            Intent(Intent.ACTION_DIAL, "tel:$dial".toUri())
                        } else {
                            Intent(Intent.ACTION_SENDTO, "mailto:$mail".toUri())
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                    height = 54.dp,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = TagsmithType.BodySmall, color = Tagsmith.colors.inkMuted, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun TagRow(tag: TagRecord) {
    val colors = Tagsmith.colors
    val visual = statusVisual(tag.status)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tag.nickname?.substringAfter("· ", tag.nickname) ?: "—",
            style = TagsmithType.Data.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.width(88.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "…" + tag.uid.shortUid().substringAfter('…'),
            style = TagsmithType.DataSmall,
            color = colors.inkFaint,
            modifier = Modifier.weight(1f),
        )
        StatusChip(visual.label, visual.fill, visual.content, small = true)
    }
    Hairline()
}

@Composable
private fun BatchRow(batch: Batch, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${batch.code} · ${batch.writtenCount} of ${batch.targetCount}", style = TagsmithType.RowTitleSmall, color = colors.ink)
            Text(
                "${batch.payloadPreview} · ${shortTime(batch.startedAt)}",
                style = TagsmithType.DataTiny,
                color = colors.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        val (fill, ink) = when (batch.status) {
            BatchStatus.DELIVERED -> colors.successTint to colors.success
            BatchStatus.COMPLETE -> colors.neutralTint to colors.inkMuted
            else -> colors.accentTint to colors.accent
        }
        StatusChip(batch.status.label, fill, ink, small = true)
    }
    Hairline()
}

@Composable
private fun TemplateRow(template: Template, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(size = 34.dp) {
            Icon(template.payloadType.icon(), null, tint = colors.ink, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(template.name, style = TagsmithType.RowTitleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(template.preview, style = TagsmithType.DataTiny, color = colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${template.useCount}×", style = TagsmithType.DataTiny, color = colors.inkFaint)
    }
    Hairline()
}

/** Free text, saved as it settles — there is no save button to forget. */
@OptIn(FlowPreview::class)
@Composable
private fun NotesEditor(initial: String, save: suspend (String) -> Unit) {
    var notes by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        snapshotFlow { notes }.drop(1).debounce(600).collect { save(it) }
    }
    Column(Modifier.padding(top = 8.dp)) {
        LabeledField(
            label = "Notes",
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Wants engraved oak coasters next run. Pays on the day. Ask for Mel.",
            singleLine = false,
            minLines = 6,
            capitalization = KeyboardCapitalization.Sentences,
            helper = "Saved as you type.",
        )
    }
}

