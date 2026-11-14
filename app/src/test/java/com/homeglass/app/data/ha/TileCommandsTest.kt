package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.EntityState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileCommandsTest {

    private fun state(entityId: String, value: String) = EntityState(entityId = entityId, state = value)

    @Test
    fun `toggle targets the entity of the domain`() {
        val call = TileCommands.toggle(state("light.salon", "on"))!!
        assertEquals("light/toggle", call.path)
        assertEquals("light.salon", call.target["entity_id"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `covers open or close depending on their state`() {
        assertEquals("cover/close_cover", TileCommands.toggle(state("cover.volet", "open"))!!.path)
        assertEquals("cover/open_cover", TileCommands.toggle(state("cover.volet", "closed"))!!.path)
    }

    @Test
    fun `locks lock or unlock`() {
        assertEquals("lock/unlock", TileCommands.toggle(state("lock.entree", "locked"))!!.path)
        assertEquals("lock/lock", TileCommands.toggle(state("lock.entree", "unlocked"))!!.path)
    }

    @Test
    fun `scenes and scripts use turn_on with a target`() {
        val scene = TileCommands.toggle(state("scene.soiree", "scening"))!!
        assertEquals("scene/turn_on", scene.path)
        assertEquals("scene.soiree", (scene.target["entity_id"] as JsonPrimitive).content)

        assertEquals("script/turn_on", TileCommands.toggle(state("script.bonjour", "off"))!!.path)
    }

    @Test
    fun `vacuum starts or docks`() {
        assertEquals("vacuum/start", TileCommands.toggle(state("vacuum.salon", "docked"))!!.path)
        assertEquals("vacuum/return_to_base", TileCommands.toggle(state("vacuum.salon", "cleaning"))!!.path)
    }

    @Test
    fun `domains without toggle have no command`() {
        assertNull(TileCommands.toggle(state("climate.salon", "heat")))
        assertNull(TileCommands.toggle(state("sensor.temperature", "21.5")))
        assertNull(TileCommands.toggle(null))
    }

    @Test
    fun `brightness zero turns the light off`() {
        val call = TileCommands.brightness("light.salon", 0)
        assertEquals("light/turn_off", call.path)
        assertEquals(emptyMap<String, Any>(), call.data)
    }

    @Test
    fun `brightness sends a clamped percentage`() {
        val call = TileCommands.brightness("light.salon", 140)
        assertEquals("light/turn_on", call.path)
        assertEquals("100", (call.data["brightness_pct"] as JsonPrimitive).content)
        assertEquals("light.salon", (call.target["entity_id"] as JsonPrimitive).content)
    }

    @Test
    fun `power commands use the domain of the entity`() {
        assertEquals("light/turn_on", TileCommands.power("light.salon", true)!!.path)
        assertEquals("switch/turn_off", TileCommands.power("switch.prise", false)!!.path)
        assertNull(TileCommands.power("sensor.temperature", true))
    }

    @Test
    fun `color commands send rgb components and kelvins`() {
        val color = TileCommands.lightColor("light.salon", 255, 149, 300)
        assertEquals("light/turn_on", color.path)
        val components = (color.data["rgb_color"] as kotlinx.serialization.json.JsonArray)
            .map { (it as JsonPrimitive).content }
        assertEquals(listOf("255", "149", "255"), components)
        assertEquals("light.salon", (color.target["entity_id"] as JsonPrimitive).content)

        val temperature = TileCommands.lightColorTemperature("light.salon", 4000)
        assertEquals("light/turn_on", temperature.path)
        assertEquals("4000", (temperature.data["color_temp_kelvin"] as JsonPrimitive).content)
        assertEquals("10000", (TileCommands.lightColorTemperature("light.salon", 50_000).data["color_temp_kelvin"] as JsonPrimitive).content)
    }

    @Test
    fun `cover position is clamped`() {
        val call = TileCommands.coverPosition("cover.volet", 120)
        assertEquals("cover/set_cover_position", call.path)
        assertEquals("100", (call.data["position"] as JsonPrimitive).content)
    }

    @Test
    fun `layout actions are forwarded verbatim`() {
        val call = TileCommands.fromLayout(
            service = "light.turn_on",
            data = mapOf("brightness_pct" to JsonPrimitive(30)),
            target = mapOf("entity_id" to JsonPrimitive("light.salon")),
        )!!

        assertEquals("light/turn_on", call.path)
        assertEquals("30", (call.data["brightness_pct"] as JsonPrimitive).content)
        assertNull(TileCommands.fromLayout("turn_on", null, null))
    }

    @Test
    fun `media commands drive playback without cutting the power`() {
        assertEquals("media_player/media_play_pause", TileCommands.mediaPlayPause("media_player.salon").path)
        assertEquals("media_player/media_next_track", TileCommands.mediaNext("media_player.salon").path)
        val previous = TileCommands.mediaPrevious("media_player.salon")
        assertEquals("media_player/media_previous_track", previous.path)
        assertEquals("media_player.salon", (previous.target["entity_id"] as JsonPrimitive).content)
    }

    @Test
    fun `volume is clamped between muted and full`() {
        val loud = TileCommands.mediaVolume("media_player.salon", 1.4)
        assertEquals("media_player/volume_set", loud.path)
        assertEquals("1.0", (loud.data["volume_level"] as JsonPrimitive).content)

        val muted = TileCommands.mediaVolume("media_player.salon", -0.2)
        assertEquals("0.0", (muted.data["volume_level"] as JsonPrimitive).content)
    }
}
