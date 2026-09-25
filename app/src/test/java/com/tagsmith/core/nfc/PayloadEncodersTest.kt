package com.tagsmith.core.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {

    @Test
    fun `encodes a person with a company as vCard 3`() {
        val card = VCard.encode(ContactFields("Sam Rowe", "+44 117 496 0000", "sam@tagsmith.co", "Tagsmith", "tagsmith.co"))
        assertEquals(
            listOf(
                "BEGIN:VCARD",
                "VERSION:3.0",
                "N:Rowe;Sam;;;",
                "FN:Sam Rowe",
                "ORG:Tagsmith",
                "TEL:+44 117 496 0000",
                "EMAIL:sam@tagsmith.co",
                "URL:tagsmith.co",
                "END:VCARD",
            ).joinToString("\r\n"),
            card,
        )
    }

    @Test
    fun `a business with no person still gets a display name`() {
        val card = VCard.encode(ContactFields(company = "Oakwell Coffee"))
        assertTrue(card.contains("FN:Oakwell Coffee"))
        assertTrue(card.contains("N:;;;;"))
    }

    @Test
    fun `leaves out fields that were not filled in`() {
        val card = VCard.encode(ContactFields(name = "Mel"))
        assertTrue(!card.contains("TEL:") && !card.contains("EMAIL:") && !card.contains("ORG:"))
    }

    @Test
    fun `escapes the characters vCard reserves, and reads them back`() {
        val original = ContactFields(name = "Fern; Bloom", company = "Kiln, Co")
        val decoded = VCard.decode(VCard.encode(original))
        assertEquals("Fern; Bloom", decoded.name)
        assertEquals("Kiln, Co", decoded.company)
    }

    @Test
    fun `decodes cards written by other apps, with type parameters`() {
        val foreign = "BEGIN:VCARD\nVERSION:2.1\nN:Rowe;Sam\nTEL;CELL;PREF:0117 496 0000\nEMAIL;INTERNET:sam@x.co\nEND:VCARD"
        val decoded = VCard.decode(foreign)
        assertEquals("Sam Rowe", decoded.name)
        assertEquals("0117 496 0000", decoded.phone)
        assertEquals("sam@x.co", decoded.email)
    }
}

class WifiCredentialTest {

    @Test
    fun `round-trips a WPA network`() {
        val wifi = WifiFields("Oakwell_Guest", WifiSecurity.WPA, "flatwhite42")
        assertEquals(wifi, WifiCredential.decode(WifiCredential.encode(wifi)))
    }

    @Test
    fun `round-trips an open network with no key`() {
        val wifi = WifiFields("Market Stall", WifiSecurity.OPEN, "")
        assertEquals(wifi, WifiCredential.decode(WifiCredential.encode(wifi)))
    }

    /**
     * Android's NfcWifiProtectedSetup looks for a Credential attribute (0x100E) at
     * the top level, then reads SSID (0x1045), Network Key (0x1027) and a two-byte
     * Auth Type (0x1003) inside it. Check the bytes, not just our own decoder.
     */
    @Test
    fun `lays out the TLVs the way Android's parser reads them`() {
        val bytes = WifiCredential.encode(WifiFields("ab", WifiSecurity.WPA, "12345678"))

        assertArrayEquals(byteArrayOf(0x10, 0x0E), bytes.copyOfRange(0, 2))
        val credentialLength = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        assertEquals(bytes.size - 4, credentialLength)

        fun indexOf(id: Int): Int {
            var i = 4
            while (i + 4 <= bytes.size) {
                val tag = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                if (tag == id) return i
                i += 4 + (((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF))
            }
            return -1
        }

        val auth = indexOf(0x1003)
        assertTrue("auth type present", auth >= 0)
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x00, 0x22), bytes.copyOfRange(auth + 2, auth + 6))

        val ssid = indexOf(0x1045)
        assertArrayEquals("ab".toByteArray(), bytes.copyOfRange(ssid + 4, ssid + 6))

        val mac = indexOf(0x1020)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, bytes.copyOfRange(mac + 4, mac + 10))
    }

    @Test
    fun `refuses a payload with no credential`() {
        assertNull(WifiCredential.decode(byteArrayOf(0x10, 0x4A, 0x00, 0x01, 0x10)))
    }

    @Test
    fun `refuses a truncated payload instead of reading past the end`() {
        assertNull(WifiCredential.decode(byteArrayOf(0x10, 0x0E, 0x00, 0x40, 0x10)))
    }
}

class PayloadUrisTest {

    @Test
    fun `keeps a leading plus and drops formatting from numbers`() {
        assertEquals("tel:+441174960000", PayloadUris.tel("+44 (0)117 496-0000".replace("(0)", "")))
        assertEquals("tel:01174960000", PayloadUris.tel("0117 496 0000"))
        assertEquals("tel:+44", PayloadUris.tel("+4+4"))
    }

    @Test
    fun `sms carries a percent-encoded body`() {
        assertEquals("sms:+441174960000?body=Table%204%20please", PayloadUris.sms("+44 117 496 0000", "Table 4 please"))
        assertEquals("sms:01174960000", PayloadUris.sms("0117 496 0000", " "))
    }

    @Test
    fun `mailto joins subject and body as query parameters`() {
        assertEquals(
            "mailto:hello@oakwell.co?subject=Booking%20enquiry&body=Hi%20%26%20thanks",
            PayloadUris.mailto(" hello@oakwell.co ", "Booking enquiry", "Hi & thanks"),
        )
        assertEquals("mailto:hello@oakwell.co", PayloadUris.mailto("hello@oakwell.co", "", ""))
    }

    @Test
    fun `geo never falls into scientific notation`() {
        assertEquals("geo:51.4545,-2.5879", PayloadUris.geo(51.4545, -2.5879))
        assertEquals("geo:0,0.00001", PayloadUris.geo(0.0, 0.00001))
    }

    @Test
    fun `address queries let the maps app resolve them`() {
        assertEquals("geo:0,0?q=14%20Perry%20Rd%2C%20Bristol", PayloadUris.geoQuery("14 Perry Rd, Bristol"))
    }
}

class HexTest {

    @Test
    fun `accepts the usual ways of writing hex`() {
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertArrayEquals(expected, Hex.parse("DE AD BE EF"))
        assertArrayEquals(expected, Hex.parse("de:ad:be:ef"))
        assertArrayEquals(expected, Hex.parse("0xDEADBEEF"))
    }

    @Test
    fun `refuses odd lengths and stray characters`() {
        assertNull(Hex.parse("ABC"))
        assertNull(Hex.parse("GG"))
        assertNull(Hex.parse(""))
    }
}
