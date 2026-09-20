package com.tagsmith.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.HistoryAction
import com.tagsmith.core.data.HistoryEntry
import com.tagsmith.ui.components.EmptyState
import com.tagsmith.ui.components.ChoiceChip
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.ScreenTitle
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.home.ActivityRow
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.shareText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryRange(val label: String, val days: Int?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All", null),
}

data class HistoryFilters(
    val range: HistoryRange = HistoryRange.WEEK,
    val action: HistoryAction? = null,
    val failuresOnly: Boolean = false,
)

data class HistoryDay(val label: String, val entries: List<HistoryEntry>)

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val ledger = container.ledger
    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters

    val entries: StateFlow<List<HistoryEntry>> = ledger.allActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val days: StateFlow<List<HistoryDay>> = combine(entries, _filters) { all, filters ->
        val cutoff = filters.range.days?.let {
            System.currentTimeMillis() - it * 24L * 60 * 60 * 1000
        } ?: 0L
        all.asSequence()
            .filter { it.timestamp >= cutoff }
            .filter { filters.action == null || it.action == filters.action }
            .filter { !filters.failuresOnly || !it.success }
            .groupBy { dayLabel(it.timestamp) }
            .map { (label, rows) -> HistoryDay(label, rows) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val failureCount: StateFlow<Int> = entries
        .let { flow ->
            combine(flow, _filters) { all, filters ->
                val cutoff = filters.range.days?.let {
                    System.currentTimeMillis() - it * 24L * 60 * 60 * 1000
                } ?: 0L
                all.count { it.timestamp >= cutoff && !it.success }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setRange(range: HistoryRange) {
        _filters.value = _filters.value.copy(range = range)
    }

    fun toggleAction(action: HistoryAction) {
        _filters.value = _filters.value.copy(
            action = if (_filters.value.action == action) null else action
        )
    }

    fun toggleFailures() {
        _filters.value = _filters.value.copy(failuresOnly = !_filters.value.failuresOnly)
    }

    /** The whole visible list as CSV, for handing to a client or a spreadsheet. */
    fun asCsv(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
        val header = "timestamp,action,uid,tag,chip,client,detail,payload,verified,outcome,error"
        val rows = days.value.flatMap { it.entries }.joinToString("\n") { entry ->
            listOf(
                format.format(Date(entry.timestamp)),
                entry.action.name,
                entry.uid,
                entry.tagLabel,
                entry.chipLabel,
                entry.clientName.orEmpty(),
                entry.detail,
                entry.payload.orEmpty(),
                entry.verified?.toString().orEmpty(),
                if (entry.success) "success" else "failure",
                entry.errorMessage.orEmpty(),
            ).joinToString(",") { field -> "\"" + field.replace("\"", "\"\"") + "\"" }
        }
        return "$header\n$rows"
    }
}

@Composable
fun HistoryScreen(onOpenEntry: (Long) -> Unit) {
    val viewModel: HistoryViewModel = containerViewModel { HistoryViewModel(it) }
    val days by viewModel.days.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val failures by viewModel.failureCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = Tagsmith.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        ScreenTitle(
            title = "History",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            trailing = {
                if (days.isNotEmpty()) {
                    TextAction(
                        label = "Export CSV",
                        onClick = { shareText(context, "Tagsmith history", viewModel.asCsv()) },
                    )
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach { range ->
                ChoiceChip(
                    label = range.label,
                    selected = filters.range == range,
                    onClick = { viewModel.setRange(range) },
                )
            }
            HistoryAction.entries.forEach { action ->
                ChoiceChip(
                    label = action.label,
                    selected = filters.action == action,
                    onClick = { viewModel.toggleAction(action) },
                )
            }
            if (failures > 0) {
                ChoiceChip(
                    label = "Failures $failures",
                    selected = filters.failuresOnly,
                    accented = true,
                    onClick = { viewModel.toggleFailures() },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (days.isEmpty()) {
            EmptyState(
                title = "Nothing in range",
                body = "Every read, write, lock and erase lands here. Widen the filters, " +
                    "or tap a tag to start the ledger off.",
                icon = TagsmithIcons.History,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 132.dp,
                ),
            ) {
                days.forEach { day ->
                    item(key = "header-${day.label}") {
                        Column(Modifier.padding(top = 16.dp)) {
                            Kicker(day.label)
                            Spacer(Modifier.height(6.dp))
                            StrongRule()
                        }
                    }
                    items(day.entries, key = { it.id }) { entry ->
                        ActivityRow(
                            entry = entry,
                            onClick = { onOpenEntry(entry.id) },
                            showWell = false,
                        )
                    }
                }
            }
        }
    }
}

/** TODAY, FRIDAY 19 SEP, 3 AUG — the ruled headers the ledger is grouped by. */
fun dayLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDelta = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else 99
    return when {
        sameYear && dayDelta == 0 -> "Today"
        sameYear && dayDelta == 1 -> "Yesterday"
        sameYear && dayDelta < 7 -> SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date(timestamp))
        sameYear -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
