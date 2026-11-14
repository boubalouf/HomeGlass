package com.homeglass.app.domain.presence

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceEntryTest {

    private val serializer = ListSerializer(PresenceEntry.serializer())

    @Test
    fun `journal entries round trip through json`() {
        val entries = listOf(
            PresenceEntry(at = 1_700_000_000_000, source = PresenceSource.Proximity, detail = "42 cm"),
            PresenceEntry(at = 1_700_000_060_000, source = PresenceSource.Camera, detail = null),
            PresenceEntry(at = 1_700_000_120_000, source = PresenceSource.Auto, detail = "Surveillance démarrée"),
        )

        val raw = Json.encodeToString(serializer, entries)
        assertEquals(entries, Json.decodeFromString(serializer, raw))
    }

    @Test
    fun `an unreadable journal decodes to null instead of throwing`() {
        val result = runCatching { Json.decodeFromString(serializer, "pas du json") }.getOrNull()
        assertEquals(null, result)
    }
}
