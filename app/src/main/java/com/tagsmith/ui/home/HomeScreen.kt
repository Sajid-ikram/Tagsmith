package com.tagsmith.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Batch
import com.tagsmith.core.data.BatchStatus
import com.tagsmith.core.data.HistoryAction
import com.tagsmith.core.data.HistoryEntry
import com.tagsmith.core.data.Template
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.IconWell
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.NoticeStrip
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

data class HomeState(
    val writtenThisWeek: Int = 0,
    val tagsOnFile: Int = 0,
    val activeClients: Int = 0,
    val recent: List<HistoryEntry> = emptyList(),
    val quickTemplates: List<Template> = emptyList(),
    val openBatch: Batch? = null,
    val openBatchClient: String? = null,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val ledger = container.ledger
    private val weekStart = startOfWeek()

    private val counts = combine(
        ledger.countSince(HistoryAction.WRITE, weekStart),
        ledger.allTags(),
        // "Active" means something happened for them in the last thirty days.
        ledger.activeClientsSince(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000),
    ) { writes, tags, active -> Triple(writes, tags.size, active) }

    private val batch = combine(container.batches.open(), container.clients.all()) { open, clients ->
        open to clients.firstOrNull { it.id == open?.clientId }?.name
    }

    val state: StateFlow<HomeState> = combine(
        counts,
        ledger.recentActivity(5),
        container.templates.quick(6),
        batch,
    ) { (writes, tags, active), recent, quick, (open, clientName) ->
        HomeState(
            writtenThisWeek = writes,
            tagsOnFile = tags,
            activeClients = active,
            recent = recent,
            quickTemplates = quick,
            openBatch = open,
            openBatchClient = clientName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * The dashboard. Numbers in mono, activity as a ruled list, and nothing
 * competing with the Scan button in the bottom third.
 */
data class HomeNav(
    val onOpenSettings: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onWrite: () -> Unit,
    val onWriteTemplate: (Long) -> Unit,
    val onTemplates: () -> Unit,
    val onNewBatch: () -> Unit,
    val onResumeBatch: (Long) -> Unit,
    val onOpenEntry: (Long) -> Unit,
)

@Composable
fun HomeScreen(availability: NfcAvailability, nav: HomeNav) {
    val viewModel: HomeViewModel = containerViewModel { HomeViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    val onOpenSettings = nav.onOpenSettings
    val onOpenNfcSettings = nav.onOpenNfcSettings
    val onOpenHistory = nav.onOpenHistory
    val onOpenEntry = nav.onOpenEntry

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Kicker(todayLabel(), color = colors.accent)
                Spacer(Modifier.height(4.dp))
                Text(greeting(), style = TagsmithType.Greeting, color = colors.ink)
            }
            com.tagsmith.ui.components.BarIcon(
                icon = TagsmithIcons.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
            )
        }

        state.openBatch?.let { batch ->
            Spacer(Modifier.height(8.dp))
            NoticeStrip(
                title = listOfNotNull(
                    if (batch.status == BatchStatus.RUNNING) "Batch running" else "Batch paused",
                    state.openBatchClient,
                ).joinToString(" — "),
                detail = "${batch.writtenCount} of ${batch.targetCount} written",
                modifier = Modifier.padding(horizontal = 16.dp),
                titleColor = colors.accent,
                trailing = { TextAction("Resume", onClick = { nav.onResumeBatch(batch.id) }) },
            )
        }

        if (availability != NfcAvailability.READY) {
            Spacer(Modifier.height(8.dp))
            NoticeStrip(
                title = if (availability == NfcAvailability.ABSENT) {
                    "This phone has no NFC radio"
                } else {
                    "NFC is switched off"
                },
                detail = if (availability == NfcAvailability.ABSENT) {
                    "reading and writing are unavailable"
                } else {
                    "tap to turn it on in system settings"
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                titleColor = Tagsmith.colors.accent,
                trailing = if (availability == NfcAvailability.DISABLED) {
                    { TextAction("Turn on", onClick = onOpenNfcSettings) }
                } else {
                    null
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        StatRow(
            stats = listOf(
                state.writtenThisWeek.toString() to "written this week",
                state.tagsOnFile.toString() to "tags on file",
                state.activeClients.toString() to "active clients",
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker("Quick templates")
                TextAction(if (state.quickTemplates.isEmpty()) "New" else "All", onClick = nav.onTemplates)
            }
        }
        QuickTemplates(state.quickTemplates, onPick = nav.onWriteTemplate, onEmpty = nav.onTemplates)

        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineAction(
                label = "Write a tag",
                onClick = nav.onWrite,
                trailingIcon = TagsmithIcons.ArrowRight,
                height = 52.dp,
                modifier = Modifier.weight(1f),
            )
            OutlineAction(
                label = "New batch",
                onClick = nav.onNewBatch,
                trailingIcon = TagsmithIcons.Batch,
                height = 52.dp,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                // Centred, not bottom-aligned: the action's tap padding is taller
                // than the kicker, so Bottom would lift its label off the line.
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Kicker("Recent activity")
                if (state.recent.isNotEmpty()) TextAction("All", onClick = onOpenHistory)
            }
            if (state.recent.isEmpty()) {
                StrongRule()
                Spacer(Modifier.height(14.dp))
                Text(
                    "Nothing yet. Tap a tag with the Scan button and it will show up here.",
                    style = TagsmithType.BodySmall,
                    color = colors.inkMuted,
                )
            } else {
                state.recent.forEach { entry ->
                    ActivityRow(entry = entry, onClick = { onOpenEntry(entry.id) })
                }
            }
        }

        // Clearance for the bottom bar and the Scan button above it.
        Spacer(Modifier.height(132.dp))
    }
}

/**
 * A horizontal row of saved payloads, favourites first. One tap goes straight
 * to the write screen with it loaded. The first carries the 2px ink border.
 */
@Composable
private fun QuickTemplates(templates: List<Template>, onPick: (Long) -> Unit, onEmpty: () -> Unit) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (templates.isEmpty()) {
            Column(
                Modifier
                    .border(1.dp, colors.borderDashed, RectangleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEmpty)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Save your first template", style = TagsmithType.RowTitleSmall.copy(fontSize = TagsmithType.ChipSelected.fontSize), color = colors.ink)
                Text("review links, Wi-Fi, contact cards", style = TagsmithType.DataTiny, color = colors.inkFaint)
            }
        }
        templates.forEachIndexed { index, template ->
            Column(
                Modifier
                    .widthIn(min = 96.dp, max = 190.dp)
                    .border(if (index == 0) 2.dp else 1.dp, if (index == 0) colors.rule else colors.border, RectangleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(template.id) },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    template.name,
                    style = TagsmithType.RowTitleSmall.copy(fontSize = TagsmithType.ChipSelected.fontSize),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(template.preview, style = TagsmithType.DataTiny, color = colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Three mono numbers between two 2px rules. */
@Composable
fun StatRow(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = Tagsmith.colors
    Column(modifier.fillMaxWidth()) {
        StrongRule()
        Row(Modifier.fillMaxWidth()) {
            stats.forEachIndexed { index, (value, caption) ->
                Column(
                    Modifier
                        .weight(1f)
                        .then(
                            if (index > 0) {
                                Modifier.padding(start = 14.dp)
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 12.dp)
                ) {
                    Text(value, style = TagsmithType.Stat, color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(caption, style = TagsmithType.StatCaption, color = colors.inkFaint)
                }
                if (index < stats.lastIndex) {
                    Box(Modifier.width(1.dp).height(62.dp).background(colors.hairlineWarm))
                }
            }
        }
        StrongRule()
    }
}

/** One line of the ledger: what happened, to which tag, when. */
@Composable
fun ActivityRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showWell: Boolean = true,
) {
    val colors = Tagsmith.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showWell) {
                IconWell(fill = if (entry.success) colors.neutralTint else colors.dangerTint) {
                    Icon(
                        imageVector = entry.action.icon(),
                        contentDescription = null,
                        tint = if (entry.success) colors.ink else colors.danger,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (entry.success) {
                        "${entry.action.label} · ${entry.tagLabel}"
                    } else {
                        "${entry.action.label} failed · ${entry.tagLabel}"
                    },
                    style = TagsmithType.RowTitleSmall,
                    color = if (entry.success) colors.ink else colors.danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.detail,
                    style = TagsmithType.RowMeta,
                    color = if (entry.success) colors.inkFaint else colors.danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = shortTime(entry.timestamp),
                style = TagsmithType.DataSmall,
                color = colors.inkFaint,
            )
        }
        Hairline()
    }
}

fun HistoryAction.icon() = when (this) {
    HistoryAction.READ -> TagsmithIcons.Read
    HistoryAction.WRITE -> TagsmithIcons.Write
    HistoryAction.LOCK -> TagsmithIcons.Lock
    HistoryAction.ERASE -> TagsmithIcons.Trash
    HistoryAction.FORMAT -> TagsmithIcons.Format
}

/** Today shows a clock; anything older shows the day. */
fun shortTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        now.timeInMillis - timestamp < 6 * 24 * 60 * 60 * 1000L -> "EEE"
        else -> "d MMM"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun todayLabel(): String =
    SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date())

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..4 -> "Still up"
    in 5..11 -> "Morning"
    in 12..17 -> "Afternoon"
    else -> "Evening"
}
