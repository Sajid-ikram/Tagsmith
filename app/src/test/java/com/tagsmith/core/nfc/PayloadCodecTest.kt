package com.tagsmith.core.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadCodecTest {

    /** One of every type, with awkward characters where they could hurt. */
    private val samples = listOf(
        NdefPayload.Url("https://g.page/r/CX8kQp2mLd9AEBM/review"),
        NdefPayload.Text("Table 4 — \"window seat\"\nThanks!", "en"),
        NdefPayload.Contact("Sam Rowe", "+44 117 496 0000", "sam@tagsmith.co", "Tagsmith", "tagsmith.co"),
        NdefPayload.Wifi("Oakwell_Guest", WifiSecurity.WPA, "flat{white}42", hidden = true),
        NdefPayload.Phone("+44 117 496 0000"),
        NdefPayload.Sms("+44 117 496 0000", "Book a table"),
        NdefPayload.Email("hello@oakwell.co", "Booking", "Hi,\nfour at 7?"),
        NdefPayload.Location("51.4545", "-2.5879", "14 Perry Rd, Bristol", byAddress = false),
        NdefPayload.App("com.oakwell.loyalty", "Oakwell Rewards"),
        NdefPayload.Raw("application/x-oakwell", "DE AD BE EF", isHex = true),
    )

    @Test
    fun `every payload type survives a round trip field for field`() {
        for (payload in samples) {
            assertEquals(payload.type.name, payload, PayloadCodec.decode(PayloadCodec.encode(payload)))
        }
    }

    @Test
    fun `covers every type the picker offers`() {
        assertEquals(PayloadType.entries.toSet(), samples.map { it.type }.toSet())
    }

    @Test
    fun `damaged JSON decodes to null rather than throwing`() {
        assertNull(PayloadCodec.decode("{not json"))
        assertNull(PayloadCodec.decode("""{"type":"HOLOGRAM"}"""))
        assertNull(PayloadCodec.decode("""{"url":"https://x.co"}"""))
    }
}
