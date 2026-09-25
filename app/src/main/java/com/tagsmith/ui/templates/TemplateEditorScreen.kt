package com.tagsmith.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.payload
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadCodec
import com.tagsmith.core.nfc.byteSize
import com.tagsmith.core.nfc.isComplete
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ClientPickerSheet
import com.tagsmith.ui.components.ClientSelectorRow
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.payload.ByteCounter
import com.tagsmith.ui.payload.PayloadDraft
import com.tagsmith.ui.payload.PayloadForm
import com.tagsmith.ui.payload.PayloadTypeRow
import com.tagsmith.ui.payload.suggestedName
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val container: AppContainer,
    private val templateId: Long?,
    prefillPayload: String?,
    prefillName: String?,
) : ViewModel() {
    val draft = PayloadDraft()
    var name by mutableStateOf(prefillName.orEmpty())
    var clientId by mutableStateOf<Long?>(null)
    var favourite by mutableStateOf(false)
    var loading by mutableStateOf(templateId != null)
        private set

    val clients: StateFlow<List<Client>> = container.clients.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isNew get() = templateId == null

    init {
        prefillPayload?.let { json -> PayloadCodec.decode(json)?.let(draft::load) }
        if (templateId != null) {
            viewModelScope.launch {
                container.templates.find(templateId)?.let { t ->
                    name = t.name
                    clientId = t.clientId
                    favourite = t.favourite
                    t.payload()?.let(draft::load)
                }
                loading = false
            }
        }
    }

    val canSave get() = !loading && draft.payload.isComplete()

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        container.templates.save(
            id = templateId ?: 0,
            name = name.trim().ifEmpty { draft.payload.suggestedName() },
            payload = draft.payload,
            clientId = clientId,
            favourite = favourite,
        )
        onSaved()
    }
}

/** Create or edit a saved payload: the same editor as Write, plus a name. */
@Composable
fun TemplateEditorScreen(
    templateId: Long?,
    prefillPayload: String?,
    prefillName: String?,
    reviewUrl: String?,
    newClientId: Long?,
    onResultConsumed: () -> Unit,
    onClose: () -> Unit,
    onOpenReviewBuilder: () -> Unit,
    onNewClient: () -> Unit,
) {
    val viewModel: TemplateEditorViewModel = containerViewModel(key = "template-$templateId") {
        TemplateEditorViewModel(it, templateId, prefillPayload, prefillName)
    }
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    var pickingClient by remember { mutableStateOf(false) }

    LaunchedEffect(reviewUrl, newClientId) {
        if (reviewUrl == null && newClientId == null) return@LaunchedEffect
        reviewUrl?.let { viewModel.draft.load(NdefPayload.Url(it)) }
        newClientId?.let { viewModel.clientId = it }
        onResultConsumed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(TagsmithIcons.Close, "Close", onClose)
            Text(
                if (viewModel.isNew) "New template" else "Edit template",
                style = TagsmithType.RowTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
        }

        if (!viewModel.loading) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                LabeledField(
                    label = "Name",
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    placeholder = viewModel.draft.payload.suggestedName(),
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
            PayloadTypeRow(
                selected = viewModel.draft.type,
                onSelect = { viewModel.draft.type = it },
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PayloadForm(viewModel.draft, onOpenReviewBuilder = onOpenReviewBuilder)
                ByteCounter(viewModel.draft.payload.byteSize())
                Column {
                    StrongRule()
                    Spacer(Modifier.height(10.dp))
                    Kicker("Details")
                    Spacer(Modifier.height(4.dp))
                    SettingRow("Favourite") {
                        SquareSwitch(viewModel.favourite, { viewModel.favourite = it })
                    }
                    ClientSelectorRow(
                        client = clients.firstOrNull { it.id == viewModel.clientId },
                        onClick = { pickingClient = true },
                        label = "Client",
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp)) {
            StrongRule()
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                label = if (viewModel.isNew) "Save template" else "Save changes",
                onClick = { viewModel.save(onClose) },
                enabled = viewModel.canSave,
                trailingIcon = TagsmithIcons.Check,
                height = 56.dp,
            )
        }
    }

    if (pickingClient) {
        ClientPickerSheet(
            clients = clients,
            selectedId = viewModel.clientId,
            onPick = {
                viewModel.clientId = it
                pickingClient = false
            },
            onNewClient = {
                pickingClient = false
                onNewClient()
            },
            onDismiss = { pickingClient = false },
        )
    }
}
