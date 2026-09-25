package com.tagsmith.ui.payload

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadType

/**
 * The payload being composed, with a slot for every type. Switching the type
 * chip keeps what was typed in the other forms, so flicking from URL to Contact
 * and back loses nothing.
 */
@Stable
class PayloadDraft(defaultType: PayloadType = PayloadType.URL) {
    var type by mutableStateOf(defaultType)
    var url by mutableStateOf("")
    var text by mutableStateOf("")
    var contact by mutableStateOf(NdefPayload.Contact())
    var wifi by mutableStateOf(NdefPayload.Wifi())
    var phone by mutableStateOf(NdefPayload.Phone())
    var sms by mutableStateOf(NdefPayload.Sms())
    var email by mutableStateOf(NdefPayload.Email())
    var location by mutableStateOf(NdefPayload.Location())
    var app by mutableStateOf(NdefPayload.App())
    var raw by mutableStateOf(NdefPayload.Raw())

    val payload: NdefPayload
        get() = when (type) {
            PayloadType.URL -> NdefPayload.Url(url)
            PayloadType.TEXT -> NdefPayload.Text(text)
            PayloadType.CONTACT -> contact
            PayloadType.WIFI -> wifi
            PayloadType.TEL -> phone
            PayloadType.SMS -> sms
            PayloadType.EMAIL -> email
            PayloadType.LOCATION -> location
            PayloadType.APP -> app
            PayloadType.RAW -> raw
        }

    /** Fills the matching form and selects its chip. */
    fun load(payload: NdefPayload) {
        type = payload.type
        when (payload) {
            is NdefPayload.Url -> url = payload.url
            is NdefPayload.Text -> text = payload.text
            is NdefPayload.Contact -> contact = payload
            is NdefPayload.Wifi -> wifi = payload
            is NdefPayload.Phone -> phone = payload
            is NdefPayload.Sms -> sms = payload
            is NdefPayload.Email -> email = payload
            is NdefPayload.Location -> location = payload
            is NdefPayload.App -> app = payload
            is NdefPayload.Raw -> raw = payload
        }
    }
}
