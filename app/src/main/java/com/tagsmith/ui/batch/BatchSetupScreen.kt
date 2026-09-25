package com.tagsmith.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.Template
import com.tagsmith.core.data.payload
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.PayloadCodec
import com.tagsmith.core.nfc.byteSize
import com.tagsmith.core.nfc.isComplete
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ChoiceChip
import com.tagsmith.ui.components.ClientPickerSheet
import com.tagsmith.ui.components.ClientSelectorRow
import com.tagsmith.ui.components.IconWell
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.SegmentedControl
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.Stepper
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TemplatePickerSheet
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.payload.ByteCounter
import com.tagsmith.ui.payload.LARGEST_STOCKED
import com.tagsmith.ui.payload.PayloadDraft
import com.tagsmith.ui.payload.PayloadForm
import com.tagsmith.ui.payload.PayloadTypeRow
import com.tagsmith.ui.payload.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BatchSetupViewModel(
    private val container: AppContainer,
    templateId: Long?,
    clientIdArg: Long?,
    payloadJson: String?,
) : ViewModel() {
    val draft = PayloadDraft()
    /** 0 = from a template, 1 = a payload composed here. */
    var source by mutableIntStateOf(if (payloadJson != null) 1 else 0)
    var template by mutableStateOf<Template?>(null)
    var clientId by mutableStateOf(clientIdArg)
    var target by mutableIntStateOf(20)
    var autoVerify by mutableStateOf(true)
    var autoLock by mutableStateOf(false)
    var starting by mutableStateOf(false)
        private set

    val clients: StateFlow<List<Client>> = container.clients.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates: StateFlow<List<Template>> = container.templates.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        payloadJson?.let { PayloadCodec.decode(it)?.let(draft::load) }
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            autoVerify = settings.verifyAfterWrite
            autoLock = settings.lockAfterWrite
            if (clientId == null) clientId = settings.defaultClientId

            val chosen = templateId?.let { container.templates.find(it) }
            if (chosen != null) {
                pickTemplate(chosen)
                // Arriving from Write with an unedited template keeps it as the source.
                if (payloadJson != null && chosen.payload() == draft.payload) source = 0
            } else if (payloadJson == null && container.templates.all().first().isEmpty()) {
                source = 1
            }
        }
    }

    fun pickTemplate(t: Template) {
        template = t
        t.clientId?.let { clientId = it }
    }

    val payload: NdefPayload?
        get() = if (source == 0) template?.payload() else draft.payload

    val canStart: Boolean
        get() = !starting && payload?.let { it.isComplete() && it.byteSize() in 1..LARGEST_STOCKED } == true

    fun start(onStarted: (Long) -> Unit) {
        val p = payload ?: return
        starting = true
        viewModelScope.launch {
            val id = container.batches.start(
                payload = p,
                clientId = clientId,
                templateId = if (source == 0) template?.id else null,
                target = target,
                autoVerify = autoVerify,
                autoLock = autoLock,
            )
            onStarted(id)
        }
    }
}

/**
 * Set up a run of identical cards: what goes on them, who they're for, how
 * many, and whether each is verified and locked as it lands.
 */
@Composable
fun BatchSetupScreen(
    templateId: Long?,
    clientId: Long?,
    payloadJson: String?,
    availability: NfcAvailability,
    newClientId: Long?,
    onResultConsumed: () -> Unit,
    onClose: () -> Unit,
    onStarted: (Long) -> Unit,
    onManageTemplates: () -> Unit,
    onNewClient: () -> Unit,
) {
    val viewModel: BatchSetupViewModel = containerViewModel(key = "batch-setup") {
        BatchSetupViewModel(it, templateId, clientId, payloadJson)
    }
    androidx.compose.runtime.LaunchedEffect(newClientId) {
        if (newClientId == null) return@LaunchedEffect
        viewModel.clientId = newClientId
        onResultConsumed()
    }
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    var pickingClient by remember { mutableStateOf(false) }
    var pickingTemplate by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(TagsmithIcons.Close, "Close", onClose)
            Text("New batch", style = TagsmithType.RowTitle, color = colors.ink, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Program a run\nof cards", style = TagsmithType.HeroSmall, color = colors.ink)
                Kicker("Payload")
                SegmentedControl(
                    options = listOf("Template", "Compose"),
                    selectedIndex = viewModel.source,
                    onSelect = { viewModel.source = it },
                )
            }

            if (viewModel.source == 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    TemplateCard(viewModel.template, onClick = { pickingTemplate = true })
                }
            } else {
                PayloadTypeRow(selected = viewModel.draft.type, onSelect = { viewModel.draft.type = it })
                PayloadForm(viewModel.draft, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ByteCounter(viewModel.payload?.byteSize() ?: 0)

                Column {
                    StrongRule()
                    ClientSelectorRow(
                        client = clients.firstOrNull { it.id == viewModel.clientId },
                        onClick = { pickingClient = true },
                        label = "For client",
                        divider = true,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kicker("How many cards")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stepper(viewModel.target, { viewModel.target = it }, range = 1..500)
                        Spacer(Modifier.width(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 20, 50).forEach { n ->
                                ChoiceChip("$n", selected = viewModel.target == n, onClick = { viewModel.target = n })
                            }
                        }
                    }
                }

                Column {
                    StrongRule()
                    Spacer(Modifier.height(10.dp))
                    Kicker("Every card")
                    Spacer(Modifier.height(4.dp))
                    SettingRow("Auto-verify") { SquareSwitch(viewModel.autoVerify, { viewModel.autoVerify = it }) }
                    SettingRow("Auto-lock", divider = !viewModel.autoLock) {
                        SquareSwitch(viewModel.autoLock, { viewModel.autoLock = it })
                    }
                    if (viewModel.autoLock) {
                        Text(
                            "Every card in this batch will be locked permanently as it's written. Nothing can undo that.",
                            style = TagsmithType.BodyTiny,
                            color = colors.danger,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp)) {
            StrongRule()
            Spacer(Modifier.height(12.dp))
            if (availability == NfcAvailability.READY) {
                PrimaryAction(
                    label = "Start batch of ${viewModel.target}",
                    onClick = { viewModel.start(onStarted) },
                    enabled = viewModel.canStart,
                    trailingIcon = TagsmithIcons.Play,
                )
            } else {
                OutlineAction(
                    label = if (availability == NfcAvailability.ABSENT) "No NFC radio on this phone" else "Switch NFC on to start",
                    onClick = {},
                    enabled = false,
                    height = 58.dp,
                )
            }
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
    if (pickingTemplate) {
        TemplatePickerSheet(
            templates = templates,
            onPick = {
                viewModel.pickTemplate(it)
                pickingTemplate = false
            },
            onManage = {
                pickingTemplate = false
                onManageTemplates()
            },
            onDismiss = { pickingTemplate = false },
        )
    }
}

@Composable
private fun TemplateCard(template: Template?, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Row(
        Modifier
            .fillMaxWidth()
            .border(2.dp, colors.rule, RectangleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (template == null) {
            Text("Choose a template", style = TagsmithType.Body, color = colors.inkFainter, modifier = Modifier.weight(1f))
        } else {
            IconWell(fill = colors.accentTint, size = 38.dp) {
                Icon(template.payloadType.icon(), null, tint = colors.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, style = TagsmithType.RowTitle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(template.preview, style = TagsmithType.DataTiny, color = colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(TagsmithIcons.ChevronDown, null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
    }
}
