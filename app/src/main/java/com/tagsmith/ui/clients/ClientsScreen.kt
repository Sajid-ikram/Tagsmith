package com.tagsmith.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.tagsmith.core.data.ClientSummary
import com.tagsmith.ui.components.ClientAvatar
import com.tagsmith.ui.components.EmptyState
import com.tagsmith.ui.components.Hairline
import com.tagsmith.ui.components.ScreenTitle
import com.tagsmith.ui.components.SearchField
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.home.shortTime
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ClientsViewModel(container: AppContainer) : ViewModel() {
    val clients: StateFlow<List<ClientSummary>?> = container.clients.summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The businesses supplied: initial in their colour, tag count, last activity. */
@Composable
fun ClientsScreen(onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val viewModel: ClientsViewModel = containerViewModel { ClientsViewModel(it) }
    val all by viewModel.clients.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    var query by remember { mutableStateOf("") }

    val clients = all.orEmpty().filter {
        query.isBlank() || it.client.name.contains(query, ignoreCase = true) ||
            it.client.address.contains(query, ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        ScreenTitle(
            title = "Clients",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            trailing = { TextAction("New client", onClick = onNew) },
        )

        if (all?.isEmpty() == true) {
            Column(Modifier.weight(1f).padding(bottom = 132.dp)) {
                EmptyState(
                    title = "No clients yet",
                    body = "Add the businesses you supply. Every tag you write for them lands on their record, so you can answer \"which card went where\" later.",
                    icon = TagsmithIcons.Clients,
                    actionLabel = "Add your first client",
                    onAction = onNew,
                )
            }
            return@Column
        }

        if ((all?.size ?: 0) > 5) {
            SearchField(query, { query = it }, "Search clients", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
        ) {
            itemsIndexed(clients, key = { _, it -> it.client.id }) { index, summary ->
                if (index == 0) StrongRule() else Hairline()
                ClientRow(summary, onClick = { onOpen(summary.client.id) })
            }
        }
    }
}

@Composable
private fun ClientRow(summary: ClientSummary, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClientAvatar(summary.client, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.client.name, style = TagsmithType.RowTitle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(if (summary.tagCount == 1) "1 tag" else "${summary.tagCount} tags")
                    summary.lastActivityAt?.let { append(" · last ${shortTime(it)}") }
                },
                style = TagsmithType.RowMeta,
                color = colors.inkFaint,
            )
        }
    }
}
