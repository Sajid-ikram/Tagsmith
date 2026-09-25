package com.tagsmith.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.DataRow
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.StatusChip
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TagsmithTopBar
import com.tagsmith.ui.home.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exactly what was written and exactly what came back on verification. */
@Composable
fun HistoryDetailScreen(entryId: Long, onBack: () -> Unit, onOpenBatch: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val entry by remember(entryId) { container.ledger.activity(entryId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val colors = Tagsmith.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        TagsmithTopBar(title = "Activity", onBack = onBack)

        val current = entry
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text("This entry is no longer in the ledger.", style = TagsmithType.Body, color = colors.inkMuted)
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(if (current.success) colors.neutralTint else colors.dangerTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = current.action.icon(),
                    contentDescription = null,
                    tint = if (current.success) colors.ink else colors.danger,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${current.action.label} · ${current.tagLabel}",
                style = TagsmithType.HeroSmall,
                color = if (current.success) colors.ink else colors.danger,
            )
            Spacer(Modifier.height(10.dp))
            StatusChip(
                label = if (current.success) "Success" else "Failed",
                fill = if (current.success) colors.successTint else colors.dangerTint,
                contentColor = if (current.success) colors.success else colors.danger,
            )

            Spacer(Modifier.height(20.dp))
            StrongRule()
            Spacer(Modifier.height(10.dp))
            Kicker("Details")
            Spacer(Modifier.height(6.dp))
            DataRow("When", fullTimestamp(current.timestamp), mono = false)
            DataRow("UID", current.uid)
            DataRow("Chip", current.chipLabel)
            current.clientName?.let { DataRow("Client", it, mono = false) }
            current.batchId?.let { batchId ->
                DataRow(
                    label = "Batch",
                    value = "B-" + batchId.toString().padStart(3, '0') + " →",
                    valueColor = colors.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenBatch(batchId) },
                    ),
                )
            }
            DataRow("Summary", current.detail, mono = false)
            current.verified?.let {
                DataRow(
                    label = "Verified",
                    value = if (it) "Yes" else "No",
                    valueColor = if (it) colors.success else colors.danger,
                    mono = false,
                )
            }

            current.payload?.let { payload ->
                Spacer(Modifier.height(20.dp))
                StrongRule()
                Spacer(Modifier.height(10.dp))
                Kicker("What was written")
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(2.dp, colors.rule, RectangleShape)
                        .padding(12.dp),
                ) {
                    Text(payload, style = TagsmithType.Data, color = colors.ink)
                }
            }

            current.readBack?.let { readBack ->
                Spacer(Modifier.height(10.dp))
                Kicker("What came back")
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.dangerBorder, RectangleShape)
                        .padding(12.dp),
                ) {
                    Text(readBack, style = TagsmithType.Data, color = colors.danger)
                }
            }

            current.errorMessage?.let { message ->
                Spacer(Modifier.height(20.dp))
                StrongRule()
                Spacer(Modifier.height(10.dp))
                Kicker("What went wrong")
                Spacer(Modifier.height(6.dp))
                Text(message, style = TagsmithType.Body, color = colors.inkMuted)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun fullTimestamp(value: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(value))
