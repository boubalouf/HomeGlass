package com.homeglass.app.domain.ha

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaInfoTest {

    private fun speaker(attributes: Map<String, kotlinx.serialization.json.JsonElement>) = EntityState(
        entityId = "media_player.salon",
        state = "playing",
        attributes = attributes,
    )

    @Test
    fun `reads title, artist, album and artwork`() {
        val info = speaker(
            mapOf(
                "media_title" to JsonPrimitive("Nuits fauves"),
                "media_artist" to JsonPrimitive("Odezenne"),
                "media_album_name" to JsonPrimitive("Void"),
                "entity_picture" to JsonPrimitive("/api/media_player_proxy/media_player.salon?token=abc"),
            ),
        ).mediaInfo()!!

        assertEquals("Nuits fauves", info.title)
        assertEquals("Odezenne", info.artist)
        assertEquals("Void", info.album)
        assertEquals(
            "http://192.168.1.204:8123/api/media_player_proxy/media_player.salon?token=abc",
            info.artworkUrl("http://192.168.1.204:8123/"),
        )
    }

    @Test
    fun `album artist is used when no artist is published`() {
        val info = speaker(mapOf("media_album_artist" to JsonPrimitive("Air"))).mediaInfo()!!
        assertEquals("Air", info.artist)
    }

    @Test
    fun `absolute artwork urls are kept as they are`() {
        val info = speaker(
            mapOf("entity_picture" to JsonPrimitive("https://music.example.com/cover.jpg")),
        ).mediaInfo()!!

        assertEquals("https://music.example.com/cover.jpg", info.artworkUrl(null))
    }

    @Test
    fun `artwork is unresolvable without a base url or a path`() {
        assertNull(MediaInfo(artworkPath = "/api/picture").artworkUrl(null))
        assertNull(MediaInfo(artworkPath = "  ").artworkUrl("http://homeassistant.local:8123"))
        assertNull(MediaInfo().artworkUrl("http://homeassistant.local:8123"))
    }

    @Test
    fun `missing attributes stay null`() {
        val info = speaker(emptyMap()).mediaInfo()!!
        assertNull(info.title)
        assertNull(info.artist)
        assertNull(info.album)
        assertNull(info.artworkPath)
    }

    @Test
    fun `other domains have no media info`() {
        assertNull(EntityState(entityId = "light.salon", state = "on").mediaInfo())
        assertNull(EntityState(entityId = "sensor.temperature", state = "21").mediaInfo())
    }
}
