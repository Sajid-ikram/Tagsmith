package com.tagsmith.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.HistoryAction
import com.tagsmith.core.data.HistoryEntry
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
    val readsThisWeek: Int = 0,
    val recent: List<HistoryEntry> = emptyList(),
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val ledger = container.ledger
    private val weekStart = startOfWeek()

    val state: StateFlow<HomeState> = combine(
        ledger.countSince(HistoryAction.WRITE, weekStart),
        ledger.countSince(HistoryAction.READ, weekStart),
        ledger.allTags(),
        ledger.recentActivity(5),
    ) { writes, reads, tags, recent ->
        HomeState(
            writtenThisWeek = writes,
            tagsOnFile = tags.size,
            readsThisWeek = reads,
            recent = recent,
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
@Composable
fun HomeScreen(
    availability: NfcAvailability,
    onOpenSettings: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onWrite: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val viewModel: HomeViewModel = containerViewModel { HomeViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors

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
                state.readsThisWeek.toString() to "reads this week",
            ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Kicker("Quick actions")
            Spacer(Modifier.height(8.dp))
            OutlineAction(
                label = "Write a tag",
                onClick = onWrite,
                trailingIcon = TagsmithIcons.ArrowRight,
                height = 52.dp,
            )
        }

        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
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
