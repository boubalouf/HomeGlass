package com.homeglass.app.domain.ha

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityPresentationTest {

    private fun state(entityId: String, value: String, attributes: String = "{}"): EntityState {
        val attrs = Json.parseToJsonElement(attributes) as JsonObject
        return EntityState(entityId = entityId, state = value, attributes = attrs.mapValues { it.value })
    }

    @Test
    fun `light on exposes brightness percentage`() {
        val presentation = state("light.salon", "on", """{"brightness":178}""").toPresentation(Locale.US)

        assertTrue(presentation.available)
        assertTrue(presentation.active)
        assertEquals(TileValueKind.Percent, presentation.kind)
        assertEquals(70, presentation.percent)
        assertEquals(0.7f, presentation.level!!, 0.01f)
    }

    @Test
    fun `light off keeps a null percentage`() {
        val presentation = state("light.salon", "off").toPresentation(Locale.US)

        assertFalse(presentation.active)
        assertEquals(TileValueKind.Off, presentation.kind)
        assertNull(presentation.percent)
    }

    @Test
    fun `unavailable entities are flagged`() {
        val presentation = state("light.salon", "unavailable").toPresentation(Locale.US)

        assertFalse(presentation.available)
        assertFalse(presentation.active)
        assertEquals(TileValueKind.None, presentation.kind)
    }

    @Test
    fun `cover exposes position`() {
        val presentation = state("cover.volet", "open", """{"current_position":45}""").toPresentation(Locale.US)

        assertTrue(presentation.active)
        assertEquals(TileValueKind.Open, presentation.kind)
        assertEquals(45, presentation.percent)
        assertEquals(0.45f, presentation.level!!, 0.01f)
    }

    @Test
    fun `locked cover is closed`() {
        val presentation = state("cover.volet", "closed", """{"current_position":0}""").toPresentation(Locale.US)
        assertEquals(TileValueKind.Closed, presentation.kind)
        assertFalse(presentation.active)
    }

    @Test
    fun `lock reports its state`() {
        assertEquals(TileValueKind.Locked, state("lock.entree", "locked").toPresentation().kind)
        assertEquals(TileValueKind.Unlocked, state("lock.entree", "unlocked").toPresentation().kind)
        assertTrue(state("lock.entree", "locked").toPresentation().active)
    }

    @Test
    fun `sensor value uses its own unit`() {
        val presentation = state(
            "sensor.temperature",
            "21.5",
            """{"unit_of_measurement":"°C"}""",
        ).toPresentation(Locale.US)

        assertEquals(TileValueKind.Measurement, presentation.kind)
        assertEquals("21.5", presentation.text)
        assertEquals("°C", presentation.unit)
    }

    @Test
    fun `numeric sensor is formatted for the locale`() {
        val presentation = state("sensor.temperature", "21.5", """{"unit_of_measurement":"°C"}""")
            .toPresentation(Locale.FRANCE)
        assertEquals("21,5", presentation.text)
    }

    @Test
    fun `binary sensor maps to on and off`() {
        assertEquals(TileValueKind.On, state("binary_sensor.porte", "on").toPresentation().kind)
        assertEquals(TileValueKind.Off, state("binary_sensor.porte", "off").toPresentation().kind)
    }

    @Test
    fun `media player exposes its playing title`() {
        val presentation = state(
            "media_player.salon",
            "playing",
            """{"media_title":"Daft Punk","volume_level":0.4}""",
        ).toPresentation()

        assertEquals(TileValueKind.Playing, presentation.kind)
        assertEquals("Daft Punk", presentation.detail)
        assertEquals(0.4f, presentation.level!!, 0.001f)
    }

    @Test
    fun `climate exposes current temperature`() {
        val presentation = state(
            "climate.salon",
            "heat",
            """{"current_temperature":19.5,"temperature_unit":"°C"}""",
        ).toPresentation(Locale.US)

        assertEquals(TileValueKind.Measurement, presentation.kind)
        assertEquals("19.5", presentation.text)
        assertTrue(presentation.active)
    }

    @Test
    fun `switch fan and vacuum are handled`() {
        assertEquals(TileValueKind.On, state("switch.prise", "on").toPresentation().kind)
        assertEquals(TileValueKind.Percent, state("fan.plafond", "on", """{"percentage":30}""").toPresentation().kind)
        assertEquals(
            "30",
            state("fan.plafond", "on", """{"percentage":30}""").toPresentation().percent.toString(),
        )
        val vacuum = state("vacuum.salon", "cleaning").toPresentation()
        assertTrue(vacuum.active)
        assertEquals("cleaning", vacuum.text)
    }
}
