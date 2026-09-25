package com.tagsmith.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Batch
import com.tagsmith.core.data.BatchStatus
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.HistoryEntry
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StatusChip
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.home.StatRow
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import com.tagsmith.ui.util.currentLocale
import com.tagsmith.ui.util.shareText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchSummaryViewModel(private val container: AppContainer, private val batchId: Long) : ViewModel() {
    val batch: StateFlow<Batch?> = container.batches.batch(batchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val writes: StateFlow<List<HistoryEntry>> = container.batches.writes(batchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val client: StateFlow<Client?> = combine(batch.filterNotNull(), container.clients.all()) { b, all ->
        all.firstOrNull { it.id == b.clientId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deliver() = viewModelScope.launch { container.batches.deliver(batchId) }

    /** One row per card, in the order they were written — what goes in the box. */
    fun csv(): String {
        val b = batch.value
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
        val header = "batch,client,card,uid,chip,written_at,verified,payload"
        val rows = writes.value.mapIndexed { index, entry ->
            listOf(
                b?.code.orEmpty(),
                client.value?.name.orEmpty(),
                (index + 1).toString(),
                entry.uid,
                entry.chipLabel,
                format.format(Date(entry.timestamp)),
                entry.verified?.toString().orEmpty(),
                entry.payload.orEmpty(),
            ).joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}

/** The batch, finished: what was written, what it cost, and handing it over. */
@Composable
fun BatchSummaryScreen(batchId: Long, onBack: () -> Unit, onResume: () -> Unit) {
    val viewModel: BatchSummaryViewModel = containerViewModel(key = "batch-summary-$batchId") {
        BatchSummaryViewModel(it, batchId)
    }
    val batch by viewModel.batch.collectAsStateWithLifecycle()
    val writes by viewModel.writes.collectAsStateWithLifecycle()
    val client by viewModel.client.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = Tagsmith.colors
    var showAll by remember { mutableStateOf(false) }

    val current = batch
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(TagsmithIcons.Back, "Back", onBack)
            Text(current?.let { "Batch ${it.code}" }.orEmpty(), style = TagsmithType.RowTitle, color = colors.ink)
        }
        if (current == null) return@Column

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            val cards = if (current.writtenCount == 1) "1 card" else "${current.writtenCount} cards"
            Text(
                client?.let { "$cards for\n${it.name}" } ?: "$cards,\nno client",
                style = TagsmithType.ScreenTitle,
                color = colors.ink,
            )
            if (current.writtenCount < current.targetCount) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Of ${current.targetCount} planned — ${if (current.status.isOpen) "still open" else "ended early"}.",
                    style = TagsmithType.BodySmall,
                    color = colors.inkMuted,
                )
            }
            current.deliveredAt?.let {
                Spacer(Modifier.height(10.dp))
                StatusChip(
                    "Delivered " + SimpleDateFormat("d MMM", currentLocale()).format(Date(it)),
                    colors.successTint,
                    colors.success,
                )
            }

            Spacer(Modifier.height(18.dp))
            StatRow(
                stats = listOf(
                    current.writtenCount.toString() to "written",
                    current.failedCount.toString() to "retried",
                    formatClock(current.elapsedMillis()) to "duration",
                ),
            )

            Spacer(Modifier.height(16.dp))
            Kicker("Payload")
            Spacer(Modifier.height(6.dp))
            Text(current.payloadPreview, style = TagsmithType.Data, color = colors.inkMuted)

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Tag UIDs", modifier = Modifier.weight(1f))
                if (writes.size > 6) TextAction(if (showAll) "Fewer" else "All ${writes.size}", onClick = { showAll = !showAll })
            }
            if (writes.isEmpty()) {
                Text("None written.", style = TagsmithType.DataSmall, color = colors.inkFaint, modifier = Modifier.padding(top = 4.dp))
            }
            val shown = if (showAll) writes else writes.take(6)
            shown.forEachIndexed { index, entry ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text((index + 1).toString().padStart(2, '0'), style = TagsmithType.DataSmall, color = colors.inkFaint)
                    Spacer(Modifier.width(12.dp))
                    Text(entry.uid, style = TagsmithType.DataSmall, color = colors.inkMuted)
                }
            }
            if (!showAll && writes.size > 6) {
                Text("… ${writes.size - 6} more", style = TagsmithType.DataSmall, color = colors.inkFaint)
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                current.status.isOpen -> PrimaryAction(
                    label = "Resume batch",
                    onClick = onResume,
                    trailingIcon = TagsmithIcons.Play,
                    height = 56.dp,
                )

                current.status == BatchStatus.COMPLETE && current.writtenCount > 0 -> PrimaryAction(
                    label = "Mark batch as delivered",
                    onClick = viewModel::deliver,
                    trailingIcon = TagsmithIcons.Check,
                    height = 56.dp,
                )

                else -> PrimaryAction(label = "Done", onClick = onBack, height = 56.dp)
            }
            OutlineAction(
                label = "Export CSV",
                onClick = { shareText(context, "Tagsmith batch ${current.code}", viewModel.csv()) },
                enabled = writes.isNotEmpty(),
                trailingIcon = TagsmithIcons.Download,
            )
        }
    }
}

/** `11:06` — the summary shows duration the way a stopwatch would. */
private fun formatClock(millis: Long): String {
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
