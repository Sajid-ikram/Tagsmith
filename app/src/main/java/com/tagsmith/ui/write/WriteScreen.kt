package com.tagsmith.ui.write

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.PayloadCodec
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.ui.components.BarIcon
import com.tagsmith.ui.components.ClientPickerSheet
import com.tagsmith.ui.components.ClientSelectorRow
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.LabeledField
import com.tagsmith.ui.components.OutlineAction
import com.tagsmith.ui.components.PrimaryAction
import com.tagsmith.ui.components.SettingRow
import com.tagsmith.ui.components.SquareSwitch
import com.tagsmith.ui.components.StrongRule
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.components.TemplatePickerSheet
import com.tagsmith.ui.components.TextAction
import com.tagsmith.ui.containerViewModel
import com.tagsmith.ui.payload.ByteCounter
import com.tagsmith.ui.payload.PayloadForm
import com.tagsmith.ui.payload.PayloadTypeRow
import com.tagsmith.ui.payload.suggestedName
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.theme.TagsmithType

/** Where Write hands off to, so the nav graph owns the routes. */
data class WriteNav(
    val onClose: () -> Unit,
    val onLockTag: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
    val onOpenReviewBuilder: () -> Unit,
    val onManageTemplates: () -> Unit,
    val onNewClient: () -> Unit,
    /** payload JSON, client, template — batch setup takes it from there. */
    val onStartBatch: (String, Long?, Long?) -> Unit,
)

/**
 * Compose a payload, then tap. The write itself never leaves this screen —
 * the tap prompt, the success and the failures are all states of it.
 */
@Composable
fun WriteScreen(
    prefill: String?,
    templateId: Long?,
    reviewUrl: String?,
    newClientId: Long?,
    onResultConsumed: () -> Unit,
    availability: NfcAvailability,
    nav: WriteNav,
) {
    val viewModel: WriteViewModel = containerViewModel(key = "write") {
        WriteViewModel(it, prefill, templateId)
    }
    val phase by viewModel.session.phase.collectAsStateWithLifecycle()

    // What the review builder or the client form hands back lands in the form.
    LaunchedEffect(reviewUrl, newClientId) {
        if (reviewUrl == null && newClientId == null) return@LaunchedEffect
        reviewUrl?.let(viewModel::setUrl)
        newClientId?.let { viewModel.clientId = it }
        onResultConsumed()
    }

    // Only this screen's own writes are drawn here; a scan, a lock or a batch
    // elsewhere in the session leaves the form untouched.
    val current = phase.takeIf {
        val op = it.forOperation
        op is TagOperation.Write && op.context.batchId == null
    } ?: NfcPhase.Idle

    // Leaving the screen must never leave the radio armed.
    DisposableEffect(Unit) { onDispose { viewModel.cancel() } }

    when (current) {
        is NfcPhase.Done -> WriteSuccessScreen(
            value = viewModel.displayValue,
            chipLabel = current.result.snapshot.chipLabel,
            uid = current.result.snapshot.uid,
            verified = current.result.verified,
            locked = current.result.snapshot.locked,
            onWriteAnother = { viewModel.cancel() },
            onLock = nav.onLockTag,
            onDone = {
                viewModel.cancel()
                nav.onClose()
            },
            note = viewModel.savedTemplateName?.let { "Saved as template “$it”" },
        )

        is NfcPhase.Failed -> WriteFailureScreen(
            failure = current.failure,
            expected = viewModel.displayValue,
            onRetry = { viewModel.retry() },
            onEdit = { viewModel.cancel() },
        )

        is NfcPhase.Waiting, is NfcPhase.Detected, is NfcPhase.Working -> TapPromptScreen(
            title = "Tap the tag to write",
            detail = buildString {
                append(if (viewModel.verifyAfterWrite) "Verify on" else "Verify off")
                append(" · ")
                append(if (viewModel.lockAfterWrite) "auto-lock on" else "auto-lock off")
            },
            value = viewModel.displayValue,
            detected = current.isEngaged,
            onCancel = { viewModel.cancel() },
        )

        NfcPhase.Idle -> ComposeForm(viewModel = viewModel, availability = availability, nav = nav)
    }
}

@Composable
private fun ComposeForm(
    viewModel: WriteViewModel,
    availability: NfcAvailability,
    nav: WriteNav,
) {
    val colors = Tagsmith.colors
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var pickingClient by remember { mutableStateOf(false) }
    var pickingTemplate by remember { mutableStateOf(false) }
    val client = clients.firstOrNull { it.id == viewModel.clientId }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(TagsmithIcons.Close, "Close", nav.onClose)
            Text("Write a tag", style = TagsmithType.RowTitle, color = colors.ink, modifier = Modifier.weight(1f))
            TextAction("Templates", onClick = { pickingTemplate = true })
        }

        PayloadTypeRow(
            selected = viewModel.draft.type,
            onSelect = { viewModel.draft.type = it },
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PayloadForm(draft = viewModel.draft, onOpenReviewBuilder = nav.onOpenReviewBuilder)

            ByteCounter(viewModel.byteSize)

            Column {
                StrongRule()
                Spacer(Modifier.height(10.dp))
                Kicker("Options")
                Spacer(Modifier.height(4.dp))
                SettingRow("Verify after writing") {
                    SquareSwitch(viewModel.verifyAfterWrite, { viewModel.verifyAfterWrite = it })
                }
                SettingRow("Lock after writing") {
                    SquareSwitch(viewModel.lockAfterWrite, { viewModel.lockAfterWrite = it })
                }
                if (viewModel.lockAfterWrite) {
                    Text(
                        text = "Locking is permanent. Once written, this tag can never be " +
                            "rewritten, erased or reformatted by anyone.",
                        style = TagsmithType.BodyTiny,
                        color = colors.danger,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                SettingRow("Save as template", divider = !viewModel.saveAsTemplate) {
                    SquareSwitch(viewModel.saveAsTemplate, { viewModel.saveAsTemplate = it })
                }
                if (viewModel.saveAsTemplate) {
                    LabeledField(
                        label = null,
                        value = viewModel.templateName,
                        onValueChange = { viewModel.templateName = it },
                        placeholder = viewModel.payload.suggestedName(),
                        capitalization = KeyboardCapitalization.Sentences,
                        helper = "Saved once the write lands.",
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                ClientSelectorRow(client = client, onClick = { pickingClient = true })
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.ground)
                .navigationBarsPadding(),
        ) {
            StrongRule()
            Row(
                Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (availability == NfcAvailability.READY) {
                    PrimaryAction(
                        label = "Write tag",
                        onClick = viewModel::startWrite,
                        trailingIcon = TagsmithIcons.Nfc,
                        enabled = viewModel.canWrite,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    OutlineAction(
                        label = if (availability == NfcAvailability.ABSENT) "No NFC radio" else "Switch NFC on",
                        onClick = nav.onOpenNfcSettings,
                        enabled = availability == NfcAvailability.DISABLED,
                        trailingIcon = TagsmithIcons.ExternalLink,
                        height = 58.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlineAction(
                    label = "Batch",
                    onClick = {
                        nav.onStartBatch(
                            PayloadCodec.encode(viewModel.payload),
                            viewModel.clientId,
                            viewModel.templateIdForWrite,
                        )
                    },
                    enabled = viewModel.canWrite,
                    height = 58.dp,
                    modifier = Modifier.width(104.dp),
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
                nav.onNewClient()
            },
            onDismiss = { pickingClient = false },
        )
    }
    if (pickingTemplate) {
        TemplatePickerSheet(
            templates = templates,
            onPick = {
                viewModel.loadTemplate(it)
                pickingTemplate = false
            },
            onManage = {
                pickingTemplate = false
                nav.onManageTemplates()
            },
            onDismiss = { pickingTemplate = false },
        )
    }
}
