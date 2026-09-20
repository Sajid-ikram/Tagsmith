package com.tagsmith.ui.write

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagsmith.AppContainer
import com.tagsmith.core.nfc.ChipType
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadType
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.byteSize
import com.tagsmith.core.nfc.isComplete
import com.tagsmith.core.nfc.normalizeUrl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Composes a payload and hands it to the session. The byte count is computed
 * from the real NDEF encoding, not the length of the typed string.
 */
class WriteViewModel(private val container: AppContainer, prefill: String?) : ViewModel() {

    var payloadType by mutableStateOf(PayloadType.URL)
        private set
    var url by mutableStateOf(prefill.orEmpty())
        private set
    var text by mutableStateOf("")
        private set
    var verifyAfterWrite by mutableStateOf(true)
        private set
    var lockAfterWrite by mutableStateOf(false)
        private set

    val session = container.nfc

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            verifyAfterWrite = settings.verifyAfterWrite
            lockAfterWrite = settings.lockAfterWrite
            if (prefill.isNullOrBlank() && settings.defaultPayloadType.available) {
                payloadType = settings.defaultPayloadType
            }
        }
    }

    fun selectType(type: PayloadType) {
        if (type.available) payloadType = type
    }

    fun updateUrl(value: String) {
        url = value
    }

    fun updateText(value: String) {
        text = value
    }

    fun updateVerify(value: Boolean) {
        verifyAfterWrite = value
    }

    fun updateLock(value: Boolean) {
        lockAfterWrite = value
    }

    val payload: NdefPayload
        get() = when (payloadType) {
            PayloadType.TEXT -> NdefPayload.Text(text)
            else -> NdefPayload.Url(url)
        }

    val byteSize: Int get() = payload.byteSize()

    val canWrite: Boolean get() = payload.isComplete() && byteSize <= LARGEST_STOCKED

    /** The value the success screen echoes back. */
    val displayValue: String
        get() = when (payloadType) {
            PayloadType.TEXT -> text
            else -> normalizeUrl(url)
        }

    /** Which of the chips you stock this payload will actually fit on. */
    val fitNote: String
        get() = when {
            byteSize == 0 -> "nothing to write yet"
            byteSize <= ChipType.NTAG213.nominalCapacity -> "fits ${ChipType.NTAG213.label}"
            byteSize <= ChipType.NTAG215.nominalCapacity -> "needs ${ChipType.NTAG215.label} or larger"
            byteSize <= ChipType.NTAG216.nominalCapacity -> "needs ${ChipType.NTAG216.label}"
            else -> "too large for any chip you stock"
        }

    val fitsSmallestChip: Boolean get() = byteSize in 1..ChipType.NTAG213.nominalCapacity

    fun startWrite() {
        session.arm(
            TagOperation.Write(
                payload = payload,
                verify = verifyAfterWrite,
                lockAfter = lockAfterWrite,
            )
        )
    }

    fun cancel() = session.cancel()

    fun retry() = session.retry()

    fun lockLastTag() = session.arm(TagOperation.Lock)

    companion object {
        /** The reference capacity the byte meter is drawn against. */
        val SMALLEST_STOCKED = ChipType.NTAG213.nominalCapacity
        val LARGEST_STOCKED = ChipType.NTAG216.nominalCapacity
    }
}
