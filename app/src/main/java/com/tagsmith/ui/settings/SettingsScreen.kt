package com.tagsmith.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Client
import com.tagsmith.core.nfc.PayloadType
import com.tagsmith.core.settings.AppSettings
import com.tagsmith.core.settings.SettingsRepository
import com.tagsmith.core.settings.ThemeChoice
import com.tagsmith.ui.components.ClientPickerSheet
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.SheetAction
import com.tagsmith.ui.components.TagsmithSheet
import com.tagsmith.ui.components.SegmentedControl
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithTopBar
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.payload.icon
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repository: SettingsRepository = container.settings
    private val ledger = container.ledger

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(value: ThemeChoice) = viewModelScope.launch { repository.setTheme(value) }
    fun setHapticDetect(value: Boolean) = viewModelScope.launch { repository.setHapticOnDetect(value) }
    fun setHapticSuccess(value: Boolean) = viewModelScope.launch { repository.setHapticOnSuccess(value) }
    fun setSounds(value: Boolean) = viewModelScope.launch { repository.setSounds(value) }
    fun setDefaultPayload(value: PayloadType) = viewModelScope.launch {
        repository.setDefaultPayloadType(value)
    }

    fun setVerify(value: Boolean) = viewModelScope.launch { repository.setVerifyAfterWrite(value) }
    fun setLock(value: Boolean) = viewModelScope.launch { repository.setLockAfterWrite(value) }
    fun setDefaultClient(id: Long?) = viewModelScope.launch { repository.setDefaultClient(id) }
    fun clearHistory() = viewModelScope.launch { ledger.clearHistory() }

    val clients: StateFlow<List<Client>> = container.clients.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SettingsScreen(onBack: () -> Unit, versionName: String, onNewClient: () -> Unit) {
    val viewModel: SettingsViewModel = containerViewModel { SettingsViewModel(it) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val colors = Tagsmith.colors
    var confirmClear by remember { mutableStateOf(false) }
    var pickingClient by remember { mutableStateOf(false) }
    var pickingType by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        TagsmithTopBar(title = "Settings", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsGroup("Appearance") {
                SegmentedControl(
                    options = ThemeChoice.entries.map { it.label },
                    selectedIndex = ThemeChoice.entries.indexOf(settings.theme),
                    onSelect = { viewModel.setTheme(ThemeChoice.entries[it]) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Dark is a first-class theme here, not an inversion — the scan " +
                        "screens use the dark ground whichever you pick.",
                    style = TagsmithType.BodyTiny,
                    color = colors.inkFaint,
                )
                Spacer(Modifier.height(6.dp))
            }

            SettingsGroup("Feedback") {
                SettingRow("Haptic on tag detected") {
                    SquareSwitch(settings.hapticOnDetect, viewModel::setHapticDetect)
                }
                SettingRow("Haptic on write success") {
                    SquareSwitch(settings.hapticOnSuccess, viewModel::setHapticSuccess)
                }
                SettingRow("Sounds") {
                    SquareSwitch(settings.sounds, viewModel::setSounds)
                }
            }

            SettingsGroup("Defaults") {
                SettingRow("Payload type", onClick = { pickingType = true }) {
                    Text(settings.defaultPayloadType.label, style = TagsmithType.RowTitleSmall, color = colors.inkFaint)
                }
                SettingRow("Default client", onClick = { pickingClient = true }) {
                    Text(
                        clients.firstOrNull { it.id == settings.defaultClientId }?.name ?: "None",
                        style = TagsmithType.RowTitleSmall,
                        color = colors.inkFaint,
                    )
                }
                SettingRow("Verify after write") {
                    SquareSwitch(settings.verifyAfterWrite, viewModel::setVerify)
                }
                SettingRow("Lock after write") {
                    SquareSwitch(settings.lockAfterWrite, viewModel::setLock)
                }
                if (settings.lockAfterWrite) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Every write will lock its tag permanently. Nothing can undo that.",
                        style = TagsmithType.BodyTiny,
                        color = colors.danger,
                    )
                }
            }

            SettingsGroup("Data") {
                Spacer(Modifier.height(4.dp))
                if (confirmClear) {
                    Text(
                        text = "Clear the whole ledger? Tag records stay; every logged read and " +
                            "write is deleted.",
                        style = TagsmithType.BodyTiny,
                        color = colors.danger,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DataChip("Keep it", danger = false) { confirmClear = false }
                        DataChip("Clear history", danger = true) {
                            viewModel.clearHistory()
                            confirmClear = false
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DataChip("Clear history", danger = true) { confirmClear = true }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SettingsGroup("About") {
                SettingRow("Version") {
                    Text(versionName, style = TagsmithType.DataSmall, color = colors.inkFaint)
                }
                SettingRow("Built for", divider = false) {
                    Text(
                        "one operator, one phone",
                        style = TagsmithType.ChipSmall,
                        color = colors.inkFaint,
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (pickingClient) {
        ClientPickerSheet(
            clients = clients,
            selectedId = settings.defaultClientId,
            onPick = {
                viewModel.setDefaultClient(it)
                pickingClient = false
            },
            onNewClient = {
                pickingClient = false
                onNewClient()
            },
            onDismiss = { pickingClient = false },
        )
    }
    if (pickingType) {
        TagsmithSheet(onDismiss = { pickingType = false }, title = "Default payload") {
            PayloadType.entries.forEach { type ->
                SheetAction(
                    label = type.label,
                    icon = type.icon(),
                    onClick = {
                        viewModel.setDefaultPayload(type)
                        pickingType = false
                    },
                    detail = if (type == settings.defaultPayloadType) "Current default" else null,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        StrongRule()
        Spacer(Modifier.height(10.dp))
        Kicker(label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DataChip(label: String, danger: Boolean, onClick: () -> Unit) {
    val colors = Tagsmith.colors
    Box(
        Modifier
            .border(1.dp, if (danger) colors.dangerBorder else colors.border, RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(label, style = TagsmithType.Chip, color = if (danger) colors.danger else colors.ink)
    }
}
