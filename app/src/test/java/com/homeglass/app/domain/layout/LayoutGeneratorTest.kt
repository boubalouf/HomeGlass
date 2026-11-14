package com.homeglass.app.domain.layout

import com.homeglass.app.domain.ha.AreaEntry
import com.homeglass.app.domain.ha.DeviceEntry
import com.homeglass.app.domain.ha.EntityEntry
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.HaRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutGeneratorTest {

    private val labels = LayoutLabels(
        home = "Maison",
        rooms = "Pièces",
        settings = "Réglages",
        scenes = "Scènes",
        lights = "Lumières",
        climate = "Climat",
        security = "Sécurité",
        covers = "Volets",
        media = "Médias",
        cameras = "Caméras",
        switches = "Prises",
        sensors = "Capteurs",
        cleaning = "Nettoyage",
        other = "Autres",
    )

    private val registry = HaRegistry(
        areas = listOf(
            AreaEntry(areaId = "salon", name = "Salon", icon = "mdi:sofa"),
            AreaEntry(areaId = "cuisine", name = "Cuisine"),
        ),
        devices = listOf(DeviceEntry(id = "dev_cuisine", areaId = "cuisine")),
        entities = listOf(
            EntityEntry(entityId = "light.salon", areaId = "salon"),
            EntityEntry(entityId = "switch.prise_cuisine", deviceId = "dev_cuisine"),
            EntityEntry(entityId = "sensor.temperature", areaId = "salon"),
            EntityEntry(entityId = "scene.soiree", areaId = "salon"),
            EntityEntry(entityId = "light.cachee", areaId = "salon", hiddenBy = "user"),
            EntityEntry(entityId = "light.desactivee", areaId = "salon", disabledBy = "user"),
            EntityEntry(entityId = "sensor.diagnostic", areaId = "salon", entityCategory = "diagnostic"),
        ),
    )

    private val states = listOf(
        EntityState("light.salon", "on"),
        EntityState("switch.prise_cuisine", "off"),
        EntityState("sensor.temperature", "21.5"),
        EntityState("scene.soiree", "scening"),
        EntityState("light.cachee", "on"),
        EntityState("light.desactivee", "on"),
        EntityState("sensor.diagnostic", "1"),
        EntityState("person.timofei", "home"),
        EntityState("update.core", "on"),
    ).associateBy { it.entityId }

    private fun generate(): Layout = LayoutGenerator.generate(
        registry = registry,
        states = states,
        labels = labels,
        revision = 9,
    )

    @Test
    fun `document uses the v1 contract and the three tabs`() {
        val layout = generate()

        assertEquals(LayoutParser.SUPPORTED_SCHEMA_VERSION, layout.schemaVersion)
        assertEquals(9, layout.revision)
        assertEquals(listOf("home", "rooms", "settings"), layout.tabs.map { it.id })
        assertEquals(listOf(TabType.Home, TabType.Rooms, TabType.Settings), layout.tabs.map { it.type })
        assertEquals("Maison", layout.tabs.first().label)
        assertEquals("Maison", layout.home.title)
    }

    @Test
    fun `generated document is parseable by the tolerant parser`() {
        val raw = LayoutParser.json.encodeToString(Layout.serializer(), generate())
        val parsed = LayoutParser.parse(raw)

        assertTrue("document généré non relisible : $parsed", parsed is LayoutParseResult.Supported)
        assertEquals(9, (parsed as LayoutParseResult.Supported).layout.revision)
    }

    @Test
    fun `scenes are exposed as the first home section`() {
        val section = generate().home.sections.first()

        assertEquals(Section.KIND_SCENES, section.kind)
        assertEquals("Scènes", section.title)

        val scene = LayoutParser.decodeItems(section).filterIsInstance<SectionItem.Scene>().single().scene
        assertEquals("soiree", scene.name)
        assertEquals("scene.turn_on", scene.actions.single().service)
        assertEquals(listOf("#FF9500", "#FF375F"), scene.gradient)
    }

    @Test
    fun `disabled hidden and diagnostic entities are excluded`() {
        val ids = generate().home.sections
            .flatMap { LayoutParser.decodeItems(it) }
            .mapNotNull { item ->
                when (item) {
                    is SectionItem.Tile -> item.tile.entityId
                    is SectionItem.Scene -> null
                }
            }

        assertTrue(ids.contains("light.salon"))
        assertTrue(ids.contains("switch.prise_cuisine"))
        assertTrue(ids.contains("sensor.temperature"))
        assertTrue(ids.none { it == "light.cachee" || it == "light.desactivee" || it == "sensor.diagnostic" })
        assertTrue(ids.none { it.startsWith("person.") || it.startsWith("update.") })
    }

    @Test
    fun `tiles get domain defaults for size display and tap`() {
        val tiles = generate().home.sections
            .filter { it.kind == Section.KIND_TILES }
            .flatMap { LayoutParser.decodeItems(it) }
            .filterIsInstance<SectionItem.Tile>()
            .map { it.tile }
            .associateBy { it.entityId }

        val light = tiles.getValue("light.salon")
        assertEquals(TileSize.Small, light.size)
        assertEquals(Display.Slider, light.display)
        assertEquals(ActionType.Toggle, light.tap?.type)
        assertEquals(ActionType.MoreInfo, light.longPress?.type)
        assertEquals("lightbulb", light.icon)

        val sensor = tiles.getValue("sensor.temperature")
        assertEquals(Display.Value, sensor.display)
        assertEquals(ActionType.MoreInfo, sensor.tap?.type)
    }

    @Test
    fun `rooms follow the area registry`() {
        val rooms = generate().rooms

        assertEquals(2, rooms.size)
        assertEquals(listOf("Cuisine", "Salon"), rooms.map { it.name })
        assertEquals("sofa", rooms.first { it.areaId == "salon" }.icon)

        val cuisine = rooms.first { it.areaId == "cuisine" }
        val cuisineTiles = LayoutParser.decodeItems(cuisine.sections.single())
            .filterIsInstance<SectionItem.Tile>()
            .map { it.tile.entityId }
        // L'entité est rattachée à la cuisine par son appareil, pas par elle-même.
        assertEquals(listOf("switch.prise_cuisine"), cuisineTiles)

        val salon = rooms.first { it.areaId == "salon" }
        val salonIds = LayoutParser.decodeItems(salon.sections.single())
            .filterIsInstance<SectionItem.Tile>()
            .map { it.tile.entityId }
        assertEquals(listOf("light.salon", "sensor.temperature"), salonIds)
    }

    @Test
    fun `an empty registry still produces a usable document`() {
        val layout = LayoutGenerator.generate(HaRegistry(), emptyMap(), labels)

        assertEquals(3, layout.tabs.size)
        assertTrue(layout.home.sections.isEmpty())
        assertTrue(layout.rooms.isEmpty())
        assertEquals("Maison", layout.home.title)
        assertNull(layout.rooms.firstOrNull())
    }
}
