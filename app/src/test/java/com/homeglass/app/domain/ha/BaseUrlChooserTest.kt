package com.homeglass.app.domain.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlChooserTest {

    @Test
    fun `internal url wins on the local network`() {
        val url = BaseUrlChooser.choose(
            internalUrl = "http://192.168.1.20:8123/",
            externalUrl = "https://maison.duckdns.org",
            fallbackUrl = "http://homeassistant.local:8123",
            onLocalNetwork = true,
        )
        assertEquals("http://192.168.1.20:8123", url)
    }

    @Test
    fun `external url wins elsewhere`() {
        val url = BaseUrlChooser.choose(
            internalUrl = "http://192.168.1.20:8123",
            externalUrl = "https://maison.duckdns.org/",
            fallbackUrl = "http://homeassistant.local:8123",
            onLocalNetwork = false,
        )
        assertEquals("https://maison.duckdns.org", url)
    }

    @Test
    fun `missing urls fall back gracefully`() {
        assertEquals(
            "http://192.168.1.20:8123",
            BaseUrlChooser.choose("http://192.168.1.20:8123", null, "http://fallback", onLocalNetwork = false),
        )
        assertEquals(
            "https://externe",
            BaseUrlChooser.choose(null, "https://externe", "http://fallback", onLocalNetwork = true),
        )
        assertEquals(
            "http://fallback",
            BaseUrlChooser.choose("", " ", "http://fallback/", onLocalNetwork = true),
        )
    }

    @Test
    fun `host is extracted from urls`() {
        assertEquals("192.168.1.20", BaseUrlChooser.host("http://192.168.1.20:8123/api/"))
        assertEquals("homeassistant.local", BaseUrlChooser.host("http://homeassistant.local:8123"))
        assertEquals("maison.example", BaseUrlChooser.host("https://maison.example"))
    }

    @Test
    fun `private hosts are recognised`() {
        listOf(
            "127.0.0.1",
            "10.1.2.3",
            "172.16.4.9",
            "172.31.255.1",
            "192.168.0.42",
            "169.254.10.1",
            "homeassistant.local",
            "localhost",
            "::1",
        ).forEach { host ->
            assertTrue("$host devrait être privé", CleartextPolicy.isPrivateHost(host))
        }
    }

    @Test
    fun `public hosts are not private`() {
        listOf("8.8.8.8", "172.32.0.1", "192.169.1.1", "maison.duckdns.org", "example.com")
            .forEach { host ->
                assertFalse("$host ne devrait pas être privé", CleartextPolicy.isPrivateHost(host))
            }
    }

    @Test
    fun `cleartext is only allowed on private addresses`() {
        assertTrue(CleartextPolicy.isAllowed("https://maison.duckdns.org"))
        assertTrue(CleartextPolicy.isAllowed("http://192.168.1.20:8123"))
        assertTrue(CleartextPolicy.isAllowed("http://homeassistant.local:8123"))
        assertFalse(CleartextPolicy.isAllowed("http://maison.duckdns.org"))
        assertFalse(CleartextPolicy.isAllowed("ftp://192.168.1.20"))
    }
}
