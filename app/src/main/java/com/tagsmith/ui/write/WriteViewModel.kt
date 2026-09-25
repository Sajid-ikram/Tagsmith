package com.tagsmith.ui.write

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.data.Client
import com.tagsmith.core.data.Template
import com.tagsmith.core.data.payload
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.NfcPhase
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.WriteContext
import com.tagsmith.core.nfc.byteSize
import com.tagsmith.core.nfc.displayValue
import com.tagsmith.core.nfc.isComplete
import com.tagsmith.ui.payload.LARGEST_STOCKED
import com.tagsmith.ui.payload.PayloadDraft
import com.tagsmith.ui.payload.suggestedName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Composes a payload and hands it to the session. The byte count is computed
 * from the real NDEF encoding, not the length of the typed string.
 */
class WriteViewModel(
    private val container: AppContainer,
    prefillUrl: String?,
    templateId: Long?,
) : ViewModel() {

    val draft = PayloadDraft()
    val session = container.nfc

    var verifyAfterWrite by mutableStateOf(true)
    var lockAfterWrite by mutableStateOf(false)
    var saveAsTemplate by mutableStateOf(false)
    var templateName by mutableStateOf("")
    var clientId by mutableStateOf<Long?>(null)

    /** Set once a successful write has saved the payload as a template. */
    var savedTemplateName by mutableStateOf<String?>(null)
        private set

    /** The template this payload came from, while it is still unedited. */
    private var sourceTemplate: Pair<Long, NdefPayload>? = null
    private var armedOp: TagOperation.Write? = null

    val clients: StateFlow<List<Client>> = container.clients.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates: StateFlow<List<Template>> = container.templates.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            verifyAfterWrite = settings.verifyAfterWrite
            lockAfterWrite = settings.lockAfterWrite
            clientId = settings.defaultClientId
            draft.type = settings.defaultPayloadType

            if (!prefillUrl.isNullOrBlank()) {
                draft.load(NdefPayload.Url(prefillUrl))
            }
            templateId?.let { id -> container.templates.find(id)?.let { applyTemplate(it) } }
        }

        // Save-as-template happens once the write lands, not when it is armed:
        // a payload that never made it onto a tag is not worth keeping yet.
        viewModelScope.launch {
            session.phase.collect { phase ->
                if (phase is NfcPhase.Done && phase.result.operation === armedOp) onWritten()
            }
        }
    }

    val payload: NdefPayload get() = draft.payload
    val byteSize: Int get() = payload.byteSize()
    val canWrite: Boolean get() = payload.isComplete() && byteSize in 1..LARGEST_STOCKED
    val displayValue: String get() = payload.displayValue()

    fun loadTemplate(template: Template) = applyTemplate(template)

    private fun applyTemplate(template: Template) {
        val payload = template.payload() ?: return
        draft.load(payload)
        sourceTemplate = template.id to payload
        template.clientId?.let { clientId = it }
        saveAsTemplate = false
    }

    /** Only counts as a use of the template if the operator didn't change it. */
    val templateIdForWrite: Long?
        get() = sourceTemplate?.takeIf { it.second == draft.payload }?.first

    fun startWrite() {
        savedTemplateName = null
        val op = TagOperation.Write(
            payload = payload,
            verify = verifyAfterWrite,
            lockAfter = lockAfterWrite,
            context = WriteContext(clientId = clientId, templateId = templateIdForWrite),
        )
        armedOp = op
        session.arm(op)
    }

    private fun onWritten() {
        if (!saveAsTemplate || savedTemplateName != null) return
        val name = templateName.trim().ifEmpty { payload.suggestedName() }
        val written = payload
        viewModelScope.launch {
            val id = container.templates.save(0, name, written, clientId, favourite = false)
            sourceTemplate = id to written
            savedTemplateName = name
            saveAsTemplate = false
        }
    }

    fun setUrl(url: String) = draft.load(NdefPayload.Url(url))

    fun cancel() = session.cancel()
    fun retry() = session.retry()
}
