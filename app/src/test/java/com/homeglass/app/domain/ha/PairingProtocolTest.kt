package com.homeglass.app.domain.ha

import com.homeglass.app.data.ha.AddonCandidates
import com.homeglass.app.data.ha.DiscoveredInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingProtocolTest {

    @Test
    fun `lit une demande d appairage`() {
        val start = PairingProtocol.parseStart(
            """
            {"pairingId":"abc123","deviceId":"dev_1","expiresAt":"2026-01-01T00:10:00+00:00",
             "expiresIn":600,"codeLength":6,"serverVersion":"0.2.0","ha_uuid":"u1"}
            """.trimIndent(),
        )

        assertEquals("abc123", start?.pairingId)
        assertEquals("dev_1", start?.deviceId)
        assertEquals(600, start?.expiresInSeconds)
        assertEquals(6, start?.codeLength)
    }

    @Test
    fun `refuse une reponse sans identifiant de demande`() {
        assertNull(PairingProtocol.parseStart("""{"deviceId":"dev_1"}"""))
        assertNull(PairingProtocol.parseStart("<html>nope</html>"))
    }

    @Test
    fun `lit le jeton d appareil`() {
        val claim = PairingProtocol.parseClaim(
            """{"token":"jeton-secret","deviceId":"dev_1","deviceName":"Pixel 7","serverVersion":"0.2.0"}""",
        )

        assertEquals("jeton-secret", claim?.token)
        assertEquals("Pixel 7", claim?.deviceName)
        assertNull(PairingProtocol.parseClaim("""{"deviceId":"dev_1"}"""))
    }

    @Test
    fun `normalise le code saisi`() {
        assertEquals("123456", PairingProtocol.normalizeCode("123 456"))
        assertEquals("123456", PairingProtocol.normalizeCode("12-34-56"))
        assertEquals("123456", PairingProtocol.normalizeCode("1234567"))
        assertEquals("", PairingProtocol.normalizeCode("abcdef"))
    }

    @Test
    fun `deduit l adresse de l add-on depuis une instance`() {
        val instance = DiscoveredInstance(
            name = "Maison",
            baseUrl = "http://192.168.1.204:8123",
            internalUrl = "http://192.168.1.204:8123",
            externalUrl = null,
            version = "2026.11.0",
            locationName = "Maison",
            uuid = "u1",
        )

        assertEquals("http://192.168.1.204:8765", AddonCandidates.forInstance(instance))
    }

    @Test
    fun `conserve https et l IPv6 entre crochets`() {
        val secure = DiscoveredInstance(
            name = "Maison",
            baseUrl = "https://maison.local:8123",
            internalUrl = null,
            externalUrl = null,
            version = null,
            locationName = null,
            uuid = null,
        )
        val ipv6 = secure.copy(baseUrl = "http://[fd00::1]:8123", internalUrl = null)

        assertEquals("https://maison.local:8765", AddonCandidates.forInstance(secure))
        assertEquals("http://[fd00::1]:8765", AddonCandidates.forInstance(ipv6))
        assertNull(AddonCandidates.forInstance(secure.copy(baseUrl = "", internalUrl = null)))
    }
}
