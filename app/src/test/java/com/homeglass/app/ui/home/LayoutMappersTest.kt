package com.homeglass.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.domain.layout.LayoutParseResult
import com.homeglass.app.domain.layout.LayoutParser
import com.homeglass.app.ui.theme.ThemeMode
import com.homeglass.app.ui.tiles.TileAction
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileDisplay
import com.homeglass.app.ui.tiles.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMappersTest {

    private fun layout(raw: String): Layout =
        (LayoutParser.parse(raw) as LayoutParseResult.Supported).layout

    @Test
    fun `tiles are mapped to the existing tile model`() {
        val home = layout(HOME).toHomeUi()
        val section = home.sections.single { it.id == "sec_eclairage" }

        assertEquals(HomeSectionKind.Tiles, section.kind)
        assertEquals(2, section.tiles.size) // la tuile hidden est retirée

        val lamp = section.tiles.first()
        assertEquals("tile_salon", lamp.id)
        assertEquals("Lampe du salon", lamp.name)
        assertEquals(TileCategory.Light, lamp.category)
        assertEquals(TileSize.Wide, lamp.size)
        assertEquals(TileDisplay.Slider, lamp.display)
        assertEquals(Color(0xFFFF9500), lamp.tint)
        assertEquals(TileAction.Toggle, lamp.tap)
        assertEquals(TileAction.MoreInfo, lamp.longPress)
        assertEquals("", lamp.stateText)

        // Une tuile sans accent ni action garde les valeurs par défaut.
        val sensor = section.tiles[1]
        assertEquals(TileCategory.Sensor, sensor.category)
        assertNull(sensor.tint)
        assertEquals(TileAction.None, sensor.tap)
        assertEquals(TileAction.MoreInfo, sensor.longPress)
    }

    @Test
    fun `name and id fall back to the entity id`() {
        val home = layout(
            """
            {
              "schemaVersion": 1,
              "home": { "sections": [{ "id": "s", "kind": "tiles", "items": [{ "entityId": "lock.entree" }] }] }
            }
            """.trimIndent(),
        ).toHomeUi()

        val tile = home.sections.single().tiles.single()
        assertEquals("lock.entree", tile.id)
        assertEquals("lock.entree", tile.name)
        assertEquals(TileCategory.Lock, tile.category)
    }

    @Test
    fun `entities are sorted into display categories by domain`() {
        val cases = mapOf(
            "light.salon" to TileCategory.Light,
            "climate.salon" to TileCategory.Climate,
            "cover.volet" to TileCategory.Cover,
            "media_player.tv" to TileCategory.Media,
            "alarm_control_panel.maison" to TileCategory.Alarm,
            "camera.entree" to TileCategory.Camera,
            "vacuum.salon" to TileCategory.Vacuum,
            "switch.prise" to TileCategory.Switch,
            "binary_sensor.porte" to TileCategory.Sensor,
            "input_select.mode" to TileCategory.Sensor,
            "inconnu.chose" to TileCategory.Generic,
        )

        cases.forEach { (entityId, expected) ->
            assertEquals("domaine de $entityId", expected, categoryFor(entityId))
        }
    }

    @Test
    fun `scene cards keep their gradient and service action`() {
        val section = layout(SCENES).toHomeUi().sections.single()

        assertEquals(HomeSectionKind.Scenes, section.kind)
        assertTrue(section.tiles.isEmpty())

        val scene = section.scenes.single()
        assertEquals("scene_evening", scene.id)
        assertEquals("Soirée", scene.name)
        assertEquals(listOf(Color(0xFFFF9500), Color(0xFFFF375F)), scene.gradient)

        val action = scene.action as TileAction.CallService
        assertEquals("scene.turn_on", action.service)
        assertEquals("scene.soiree", action.target?.get("entity_id")?.toString()?.trim('"'))
    }

    @Test
    fun `incomplete gradients are completed to two colours`() {
        assertEquals(2, gradientColors(listOf("#5E5CE6")).size)
        assertEquals(2, gradientColors(emptyList()).size)
        assertEquals(2, gradientColors(listOf("pas une couleur", "#5E5CE6", "#BF5AF2")).size)
        assertEquals(
            listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2)),
            gradientColors(listOf("#5E5CE6", "#BF5AF2", "#FF375F")),
        )
    }

    @Test
    fun `hex colours accept rgb and argb and reject the rest`() {
        assertEquals(Color(0xFFFF9500), parseHexColor("#FF9500"))
        assertEquals(Color(0xFFFF9500), parseHexColor("FF9500"))
        assertEquals(Color(0x80FF9500), parseHexColor("#80FF9500"))
        assertNull(parseHexColor(null))
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#FF95"))
        assertNull(parseHexColor("#ZZZZZZ"))
    }

    @Test
    fun `theme is applied to the theme configuration`() {
        val config = layout(THEME).theme.toThemeConfig()

        assertEquals(ThemeMode.Dark, config.mode)
        assertEquals(Color(0xFF5E5CE6), config.accentColor)
        assertEquals(26.dp, config.tileCornerRadius)
        assertEquals(1.1f, config.densityScale)

        val wallpaper = config.wallpaper!!
        assertEquals("/api/assets/asset_salon", wallpaper.url)
        assertEquals(0.4f, wallpaper.scrim)
        assertEquals(12.dp, wallpaper.blurRadius)
    }

    @Test
    fun `gradient backgrounds do not create a wallpaper`() {
        val config = layout(HOME).theme.toThemeConfig()
        assertEquals(ThemeMode.Auto, config.mode)
        assertEquals(0.9f, config.densityScale)
        assertEquals(22.dp, config.tileCornerRadius)
        assertNull(config.wallpaper)
    }

    @Test
    fun `tabs are generated from the layout and disabled ones are dropped`() {
        val home = layout(TABS).toHomeUi()

        assertEquals(listOf("home", "rooms"), home.tabs.map { it.id })
        assertEquals("Maison", home.tabs.first().label)
        assertEquals("Pièces", home.tabs[1].label)
    }

    @Test
    fun `empty sections are not rendered`() {
        val home = layout(HOME).toHomeUi()
        assertTrue(home.sections.none { it.id == "sec_vide" })
        assertEquals("Maison", home.title)
        assertEquals(5, home.chips.size)
    }

    private companion object {
        val HOME = """
            {
              "schemaVersion": 1,
              "revision": 5,
              "theme": { "mode": "auto", "accent": "#FF9500", "tileRadiusDp": 22, "density": "compact" },
              "tabs": [{ "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true }],
              "home": {
                "title": "Maison",
                "summaryChips": ["lights", "climate", "security", "media", "energy"],
                "sections": [
                  {
                    "id": "sec_eclairage",
                    "kind": "tiles",
                    "title": "Éclairage",
                    "items": [
                      {
                        "id": "tile_salon",
                        "entityId": "light.salon",
                        "name": "Lampe du salon",
                        "icon": "lightbulb",
                        "accent": "#FF9500",
                        "size": "wide",
                        "display": "slider",
                        "tap": { "type": "toggle" },
                        "longPress": { "type": "more_info" }
                      },
                      {
                        "id": "tile_capteur",
                        "entityId": "sensor.temperature",
                        "name": "Température",
                        "icon": "sensors",
                        "accent": null,
                        "size": "small",
                        "display": "value",
                        "tap": { "type": "none" },
                        "longPress": null
                      },
                      {
                        "id": "tile_cachee",
                        "entityId": "light.couloir",
                        "name": "Couloir",
                        "icon": "lightbulb",
                        "hidden": true
                      }
                    ]
                  },
                  { "id": "sec_vide", "kind": "tiles", "title": "Vide", "items": [] }
                ]
              }
            }
        """.trimIndent()

        val SCENES = """
            {
              "schemaVersion": 1,
              "home": {
                "sections": [{
                  "id": "sec_scenes",
                  "kind": "scenes",
                  "title": "Scènes",
                  "items": [{
                    "id": "scene_evening",
                    "name": "Soirée",
                    "icon": "bedtime",
                    "gradient": ["#FF9500", "#FF375F"],
                    "actions": [{ "type": "call_service", "service": "scene.turn_on", "target": { "entity_id": "scene.soiree" } }]
                  }]
                }]
              }
            }
        """.trimIndent()

        val THEME = """
            {
              "schemaVersion": 1,
              "theme": {
                "mode": "dark",
                "accent": "#5E5CE6",
                "tileRadiusDp": 26,
                "density": "comfortable",
                "background": { "type": "image", "assetId": "asset_salon", "blurDp": 12, "dim": 0.4 }
              }
            }
        """.trimIndent()

        val TABS = """
            {
              "schemaVersion": 1,
              "tabs": [
                { "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true },
                { "id": "rooms", "type": "rooms", "label": "Pièces", "icon": "meeting_room", "enabled": true },
                { "id": "settings", "type": "settings", "label": "Réglages", "icon": "settings", "enabled": false }
              ]
            }
        """.trimIndent()
    }
}
