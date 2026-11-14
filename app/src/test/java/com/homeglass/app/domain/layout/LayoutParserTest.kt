package com.homeglass.app.domain.layout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le parseur est la frontière avec l'add-on : il ne doit jamais lever d'exception, et il doit
 * rester lisible face à un JSON partiel, plus récent ou corrompu.
 */
class LayoutParserTest {

    private fun supported(raw: String): Layout =
        (LayoutParser.parse(raw) as? LayoutParseResult.Supported)?.layout
            ?: error("document attendu comme supporté, reçu ${LayoutParser.parse(raw)}")

    @Test
    fun `reference document is fully parsed`() {
        val layout = supported(REFERENCE)

        assertEquals(1, layout.schemaVersion)
        assertEquals(42, layout.revision)
        assertEquals("2026-11-04T13:00:00Z", layout.publishedAt)
        assertEquals("#FF9500", layout.theme.accent)
        assertEquals(22, layout.theme.tileRadiusDp)
        assertEquals(ThemeMode.Auto, layout.theme.mode)
        assertEquals(Density.Standard, layout.theme.density)
        assertEquals(BackgroundType.Gradient, layout.theme.background.type)
        assertEquals(3, layout.tabs.size)
        assertEquals(TabType.Settings, layout.tabs.last().type)
        assertEquals("Maison", layout.home.title)
        assertEquals(4, layout.home.summaryChips.size)
        assertEquals(listOf("lights", "climate", "security", "media"), layout.home.summaryChips)
        assertEquals(2, layout.home.sections.size)
        assertEquals(1, layout.rooms.size)
        assertEquals("salon", layout.rooms.single().areaId)
    }

    @Test
    fun `unknown fields are ignored at every level`() {
        val raw = """
            {
              "schemaVersion": 1,
              "revision": 7,
              "futureTopLevel": { "a": 1 },
              "theme": { "mode": "dark", "neonGlow": true },
              "tabs": [{ "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true, "badge": 3 }],
              "home": {
                "title": "Chez moi",
                "sections": [{
                  "id": "sec_1",
                  "kind": "tiles",
                  "title": "Favoris",
                  "collapsed": false,
                  "items": [{ "id": "t", "entityId": "light.a", "name": "A", "icon": "lightbulb", "showGraph": true }]
                }]
              },
              "rooms": [{ "id": "r", "name": "Salon", "sections": [], "order": 2 }]
            }
        """.trimIndent()

        val layout = supported(raw)
        assertEquals(ThemeMode.Dark, layout.theme.mode)
        assertEquals("Chez moi", layout.home.title)
        assertEquals(1, LayoutParser.decodeItems(layout.home.sections.single()).size)
    }

    @Test
    fun `missing optional fields fall back to explicit defaults`() {
        val layout = supported("""{"schemaVersion": 1, "revision": 3}""")

        assertEquals(3, layout.revision)
        assertEquals("", layout.publishedAt)
        assertEquals("#FF9500", layout.theme.accent)
        assertEquals(22, layout.theme.tileRadiusDp)
        assertEquals(Density.Standard, layout.theme.density)
        assertNull(layout.theme.background.assetId)
        assertTrue(layout.tabs.isEmpty())
        assertEquals("Maison", layout.home.title)
        assertTrue(layout.home.sections.isEmpty())
        assertTrue(layout.rooms.isEmpty())
    }

    @Test
    fun `explicit nulls sent by the server are accepted`() {
        val layout = supported(NULLS)

        // accent: null → la valeur par défaut s'applique (coerceInputValues).
        assertEquals("#FF9500", layout.theme.accent)
        // assetId: null reste null : pas d'image de fond.
        assertNull(layout.theme.background.assetId)

        val tile = LayoutParser.decodeItems(layout.home.sections.first())
            .filterIsInstance<SectionItem.Tile>()
            .single()
            .tile
        assertNull(tile.accent)
        assertNull(tile.tap)
        assertNull(tile.longPress)

        val scene = LayoutParser.decodeItems(layout.home.sections[1])
            .filterIsInstance<SectionItem.Scene>()
            .single()
            .scene
        val action = scene.actions.single()
        assertNull(action.data)
        assertNull(action.target)
        assertEquals("scene.turn_on", action.service)
    }

    @Test
    fun `newer schema version is detected without throwing`() {
        val result = LayoutParser.parse("""{"schemaVersion": 2, "revision": 99}""")

        assertTrue(result is LayoutParseResult.TooNew)
        assertEquals(2, (result as LayoutParseResult.TooNew).schemaVersion)
    }

    @Test
    fun `corrupted documents are reported as invalid`() {
        assertTrue(LayoutParser.parse("{ pas du json ") is LayoutParseResult.Invalid)
        assertTrue(LayoutParser.parse("[]") is LayoutParseResult.Invalid)
        assertTrue(LayoutParser.parse("") is LayoutParseResult.Invalid)
        assertTrue(LayoutParser.parse("   ") is LayoutParseResult.Invalid)
        // Champ d'un type inattendu : erreur de structure, jamais de crash.
        assertTrue(LayoutParser.parse("""{"schemaVersion": 1, "revision": "beaucoup"}""") is LayoutParseResult.Invalid)
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        val layout = supported(
            """
            {
              "schemaVersion": 1,
              "theme": { "mode": "sepia", "density": "gigantesque", "background": { "type": "hologramme" } },
              "tabs": [{ "id": "x", "type": "media", "label": "X", "icon": "inconnue", "enabled": true }],
              "home": {
                "summaryChips": ["lights", "teleportation"],
                "sections": [{
                  "id": "s",
                  "kind": "tiles",
                  "items": [{ "id": "t", "entityId": "light.a", "size": "immense", "display": "hologramme" }]
                }]
              }
            }
            """.trimIndent(),
        )

        assertEquals(ThemeMode.Auto, layout.theme.mode)
        assertEquals(Density.Standard, layout.theme.density)
        assertEquals(BackgroundType.Gradient, layout.theme.background.type)
        assertEquals(TabType.Home, layout.tabs.single().type)
        // Une catégorie de chip inconnue est conservée brute puis ignorée à l'affichage :
        // elle ne doit jamais faire échouer le document.
        assertEquals(listOf("lights", "teleportation"), layout.home.summaryChips)

        val tile = LayoutParser.decodeItems(layout.home.sections.single())
            .filterIsInstance<SectionItem.Tile>()
            .single()
            .tile
        assertEquals(TileSize.Small, tile.size)
        assertEquals(Display.Auto, tile.display)
    }

    @Test
    fun `section items are decoded according to the section kind`() {
        val layout = supported(REFERENCE)
        val scenes = layout.home.sections.first()
        val tiles = layout.home.sections[1]

        assertEquals(Section.KIND_SCENES, scenes.kind)
        assertEquals(Section.KIND_TILES, tiles.kind)

        val scene = LayoutParser.decodeItems(scenes).filterIsInstance<SectionItem.Scene>().single()
        assertEquals(2, scene.scene.gradient.size)

        val tile = LayoutParser.decodeItems(tiles).filterIsInstance<SectionItem.Tile>().single()
        assertEquals("light.salon", tile.tile.entityId)
        assertEquals(Display.Auto, tile.tile.display)
    }

    @Test
    fun `an unreadable item does not discard the rest of the section`() {
        val layout = supported(
            """
            {
              "schemaVersion": 1,
              "home": {
                "sections": [{
                  "id": "s",
                  "kind": "tiles",
                  "items": [
                    "pas un objet",
                    { "id": "ok", "entityId": "light.b", "name": "B" }
                  ]
                }]
              }
            }
            """.trimIndent(),
        )

        val items = LayoutParser.decodeItems(layout.home.sections.single())
        assertEquals(1, items.size)
        assertEquals("light.b", (items.single() as SectionItem.Tile).tile.entityId)
    }

    @Test
    fun `fake layout shipped in assets is parseable`() {
        val file = File("src/main/assets/layout_sample.json")
        assertTrue("asset factice manquant : ${file.absolutePath}", file.exists())

        val layout = supported(file.readText())
        assertEquals(LayoutParser.SUPPORTED_SCHEMA_VERSION, layout.schemaVersion)
        assertEquals(3, layout.tabs.size)
        assertTrue(layout.home.sections.size >= 4)
        // La section au kind inconnu est traitée comme une section de tuiles.
        val unknownKind = layout.home.sections.single { it.kind == "kind_inconnu_du_client" }
        assertEquals(1, LayoutParser.decodeItems(unknownKind).size)
    }

    @Test
    fun `obsolete automations tab is dropped from the menu`() {
        // Le document de référence contient encore un onglet « automations » : il ne doit
        // pas apparaître, et les autres onglets restent dans l'ordre.
        val layout = supported(REFERENCE)

        assertEquals(listOf("home", "rooms", "settings"), layout.tabs.map { it.id })
        assertTrue(layout.tabs.none { it.id == "auto" })
    }

    private companion object {
        val REFERENCE = """
            {
              "schemaVersion": 1,
              "revision": 42,
              "publishedAt": "2026-11-04T13:00:00Z",
              "theme": {
                "mode": "auto",
                "accent": "#FF9500",
                "tileRadiusDp": 22,
                "density": "standard",
                "background": { "type": "gradient", "assetId": null, "blurDp": 0, "dim": 0.0 }
              },
              "tabs": [
                { "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true },
                { "id": "rooms", "type": "rooms", "label": "Pièces", "icon": "meeting_room", "enabled": true },
                { "id": "auto", "type": "automations", "label": "Automatisations", "icon": "bolt", "enabled": true },
                { "id": "settings", "type": "settings", "label": "Réglages", "icon": "settings", "enabled": true }
              ],
              "home": {
                "title": "Maison",
                "summaryChips": ["lights", "climate", "security", "media"],
                "sections": [
                  {
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
                  },
                  {
                    "id": "sec_fav",
                    "kind": "tiles",
                    "title": "Favoris",
                    "items": [{
                      "id": "tile_1",
                      "entityId": "light.salon",
                      "name": "Lampe salon",
                      "icon": "lightbulb",
                      "accent": null,
                      "size": "small",
                      "display": "auto",
                      "hidden": false,
                      "tap": { "type": "toggle" },
                      "longPress": { "type": "more_info" }
                    }]
                  }
                ]
              },
              "rooms": [{ "id": "room_salon", "name": "Salon", "icon": "weekend", "areaId": "salon", "sections": [] }]
            }
        """.trimIndent()

        val NULLS = """
            {
              "schemaVersion": 1,
              "revision": 8,
              "theme": { "accent": null, "background": { "type": "gradient", "assetId": null, "blurDp": 0, "dim": 0.0 } },
              "tabs": [{ "id": "home", "type": "home", "label": "Maison", "icon": "home", "enabled": true }],
              "home": {
                "title": "Maison",
                "sections": [
                  {
                    "id": "sec_fav",
                    "kind": "tiles",
                    "title": "Favoris",
                    "items": [{
                      "id": "tile_1",
                      "entityId": "light.salon",
                      "name": "Lampe salon",
                      "icon": "lightbulb",
                      "accent": null,
                      "size": "small",
                      "display": "auto",
                      "hidden": false,
                      "tap": null,
                      "longPress": null
                    }]
                  },
                  {
                    "id": "sec_scenes",
                    "kind": "scenes",
                    "title": "Scènes",
                    "items": [{
                      "id": "scene_1",
                      "name": "Soirée",
                      "icon": "bedtime",
                      "gradient": ["#FF9500", "#FF375F"],
                      "actions": [{ "type": "call_service", "service": "scene.turn_on", "data": null, "target": null, "roomId": null }]
                    }]
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
