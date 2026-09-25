package com.tagsmith.core.nfc

import org.json.JSONException
import org.json.JSONObject

/**
 * Payloads as JSON, so templates and batches can store what they write and
 * give it back field for field — not a flattened string that has to be parsed.
 */
object PayloadCodec {

    fun encode(payload: NdefPayload): String = JSONObject().apply {
        put("type", payload.type.name)
        when (payload) {
            is NdefPayload.Url -> put("url", payload.url)
            is NdefPayload.Text -> {
                put("text", payload.text)
                put("language", payload.language)
            }

            is NdefPayload.Contact -> {
                put("name", payload.name)
                put("phone", payload.phone)
                put("email", payload.email)
                put("company", payload.company)
                put("website", payload.website)
            }

            is NdefPayload.Wifi -> {
                put("ssid", payload.ssid)
                put("security", payload.security.name)
                put("password", payload.password)
                put("hidden", payload.hidden)
            }

            is NdefPayload.Phone -> put("number", payload.number)
            is NdefPayload.Sms -> {
                put("number", payload.number)
                put("body", payload.body)
            }

            is NdefPayload.Email -> {
                put("address", payload.address)
                put("subject", payload.subject)
                put("body", payload.body)
            }

            is NdefPayload.Location -> {
                put("latitude", payload.latitude)
                put("longitude", payload.longitude)
                put("address", payload.address)
                put("byAddress", payload.byAddress)
            }

            is NdefPayload.App -> {
                put("packageName", payload.packageName)
                put("label", payload.label)
            }

            is NdefPayload.Raw -> {
                put("mimeType", payload.mimeType)
                put("payload", payload.payload)
                put("isHex", payload.isHex)
            }
        }
    }.toString()

    /** Null when the JSON is damaged or names a type this build does not know. */
    fun decode(json: String): NdefPayload? = try {
        val o = JSONObject(json)
        when (PayloadType.valueOf(o.getString("type"))) {
            PayloadType.URL -> NdefPayload.Url(o.optString("url"))
            PayloadType.TEXT -> NdefPayload.Text(o.optString("text"), o.optString("language", "en"))
            PayloadType.CONTACT -> NdefPayload.Contact(
                name = o.optString("name"),
                phone = o.optString("phone"),
                email = o.optString("email"),
                company = o.optString("company"),
                website = o.optString("website"),
            )

            PayloadType.WIFI -> NdefPayload.Wifi(
                ssid = o.optString("ssid"),
                security = runCatching { WifiSecurity.valueOf(o.optString("security")) }
                    .getOrDefault(WifiSecurity.WPA),
                password = o.optString("password"),
                hidden = o.optBoolean("hidden"),
            )

            PayloadType.TEL -> NdefPayload.Phone(o.optString("number"))
            PayloadType.SMS -> NdefPayload.Sms(o.optString("number"), o.optString("body"))
            PayloadType.EMAIL -> NdefPayload.Email(
                address = o.optString("address"),
                subject = o.optString("subject"),
                body = o.optString("body"),
            )

            PayloadType.LOCATION -> NdefPayload.Location(
                latitude = o.optString("latitude"),
                longitude = o.optString("longitude"),
                address = o.optString("address"),
                byAddress = o.optBoolean("byAddress"),
            )

            PayloadType.APP -> NdefPayload.App(o.optString("packageName"), o.optString("label"))
            PayloadType.RAW -> NdefPayload.Raw(
                mimeType = o.optString("mimeType"),
                payload = o.optString("payload"),
                isHex = o.optBoolean("isHex"),
            )
        }
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
