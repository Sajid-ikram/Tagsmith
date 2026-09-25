package com.tagsmith.ui.clients

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Client
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ClientAvatar
import com.tagsmith.ui.components.ColorSwatches
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.MiniChip
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TagsmithSheet
import com.tagsmith.ui.components.paletteColorFor
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClientEditorViewModel(private val container: AppContainer, private val clientId: Long?) : ViewModel() {
    var name by mutableStateOf("")
    var contactName by mutableStateOf("")
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var address by mutableStateOf("")
    var website by mutableStateOf("")
    var color by mutableIntStateOf(paletteColorFor(0).toArgb())
    var logoPath by mutableStateOf<String?>(null)
    var importingLogo by mutableStateOf(false)
        private set
    var loading by mutableStateOf(clientId != null)
        private set

    private var original: Client? = null
    /** Logos picked in this session but not kept — deleted if the form is abandoned. */
    private val strayLogos = mutableListOf<String>()

    val isNew get() = clientId == null
    val canSave get() = !loading && name.isNotBlank()

    init {
        viewModelScope.launch {
            if (clientId != null) {
                container.clients.find(clientId)?.let { c ->
                    original = c
                    name = c.name
                    contactName = c.contactName
                    phone = c.phone
                    email = c.email
                    address = c.address
                    website = c.website
                    color = c.color
                    logoPath = c.logoPath
                }
                loading = false
            } else {
                // A new client takes the next colour along, so neighbours differ.
                color = paletteColorFor(container.clients.all().first().size).toArgb()
            }
        }
    }

    fun pickLogo(uri: android.net.Uri) = viewModelScope.launch {
        importingLogo = true
        val path = container.clients.importLogo(uri)
        importingLogo = false
        if (path != null) {
            if (logoPath != null && logoPath != original?.logoPath) container.clients.deleteLogoFile(logoPath)
            logoPath = path
            strayLogos += path
        }
    }

    fun removeLogo() {
        if (logoPath != null && logoPath != original?.logoPath) container.clients.deleteLogoFile(logoPath)
        logoPath = null
    }

    fun save(onSaved: (Long) -> Unit) = viewModelScope.launch {
        val base = original ?: Client(name = "", color = color, createdAt = System.currentTimeMillis())
        val id = container.clients.save(
            base.copy(
                name = name.trim(),
                contactName = contactName.trim(),
                phone = phone.trim(),
                email = email.trim(),
                address = address.trim(),
                website = website.trim(),
                color = color,
                logoPath = logoPath,
            )
        )
        // The old logo is only removed once the new one is safely on the record,
        // and the new one is no longer a stray to be cleaned up.
        original?.logoPath?.takeIf { it != logoPath }?.let(container.clients::deleteLogoFile)
        strayLogos.clear()
        onSaved(id)
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        original?.let { container.clients.delete(it) }
        onDeleted()
    }

    override fun onCleared() {
        strayLogos.filter { it != original?.logoPath }.forEach(container.clients::deleteLogoFile)
    }
}

@Composable
fun ClientEditorScreen(
    clientId: Long?,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: ClientEditorViewModel = containerViewModel(key = "client-edit-$clientId") {
        ClientEditorViewModel(it, clientId)
    }
    val colors = Tagsmith.colors
    var confirmingDelete by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickLogo(uri)
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
                if (viewModel.isNew) "New client" else "Edit client",
                style = TagsmithType.RowTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClientAvatar(
                    name = viewModel.name.ifBlank { "?" },
                    color = Color(viewModel.color),
                    logoPath = viewModel.logoPath,
                    size = 72.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kicker("Logo")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip(
                            when {
                                viewModel.importingLogo -> "Adding…"
                                viewModel.logoPath == null -> "Add logo"
                                else -> "Change"
                            },
                            onClick = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        if (viewModel.logoPath != null) MiniChip("Remove", onClick = viewModel::removeLogo, danger = true)
                    }
                }
            }

            LabeledField(
                "Business name", viewModel.name, { viewModel.name = it },
                placeholder = "Oakwell Coffee", capitalization = KeyboardCapitalization.Words,
            )
            LabeledField(
                "Contact", viewModel.contactName, { viewModel.contactName = it },
                placeholder = "Mel", capitalization = KeyboardCapitalization.Words,
            )
            LabeledField(
                "Phone", viewModel.phone, { viewModel.phone = it },
                placeholder = "+44 117 496 0000", mono = true, keyboardType = KeyboardType.Phone,
            )
            LabeledField(
                "Email", viewModel.email, { viewModel.email = it },
                placeholder = "hello@oakwell.co", mono = true, keyboardType = KeyboardType.Email,
            )
            LabeledField(
                "Address", viewModel.address, { viewModel.address = it },
                placeholder = "14 Perry Rd, Bristol", capitalization = KeyboardCapitalization.Words,
            )
            LabeledField(
                "Website", viewModel.website, { viewModel.website = it },
                placeholder = "oakwell.co", mono = true, keyboardType = KeyboardType.Uri,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Colour")
                ColorSwatches(selected = viewModel.color, onSelect = { viewModel.color = it })
            }

            if (!viewModel.isNew) {
                StrongRule()
                OutlineAction(
                    label = "Delete client",
                    onClick = { confirmingDelete = true },
                    borderColor = colors.dangerBorder,
                    contentColor = colors.danger,
                    trailingIcon = TagsmithIcons.Trash,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp)) {
            PrimaryAction(
                label = if (viewModel.isNew) "Add client" else "Save changes",
                onClick = { viewModel.save(onSaved) },
                enabled = viewModel.canSave,
                trailingIcon = TagsmithIcons.Check,
                height = 56.dp,
            )
        }
    }

    if (confirmingDelete) {
        TagsmithSheet(onDismiss = { confirmingDelete = false }, title = "Delete ${viewModel.name}?") {
            Text(
                "Their tags, templates and batches stay — they just stop belonging to anyone. " +
                    "History keeps the name it logged at the time.",
                style = TagsmithType.BodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction(label = "Keep them", onClick = { confirmingDelete = false })
                PrimaryAction(
                    label = "Delete client",
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete(onDeleted)
                    },
                    background = colors.danger,
                    height = 52.dp,
                    loud = false,
                )
            }
        }
    }
}
