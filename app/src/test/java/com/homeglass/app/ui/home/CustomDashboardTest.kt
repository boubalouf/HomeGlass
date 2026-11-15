package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.domain.dashboard.PlacedTile
import com.homeglass.app.domain.dashboard.WidgetItem
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDashboardTest {

    private fun tile(entityId: String) = TileUiModel(
        id = "tile_$entityId",
        name = entityId,
        stateText = "",
        category = TileCategory.Light,
        entityId = entityId,
    )

    private fun home(sections: List<HomeSectionUi>) = HomeUiModel(
        title = "Maison",
        revision = 1,
        chips = emptyList(),
        sections = sections,
        tabs = emptyList(),
    )

    @Test
    fun `empty dashboard keeps the generated dashboard`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "Lumières", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        assertEquals(model, model.withCustomLayout(CustomDashboard()))
    }

    @Test
    fun `placed tiles build a freeform section with their geometry`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "Lumières", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"), tile("light.b"))),
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(
                tiles = listOf(
                    PlacedTile(id = "tile_light.b", entityId = "light.b", col = 2, row = 1, spanX = 2, spanY = 1),
                    PlacedTile(id = "tile_light.a", entityId = "light.a", col = 0, row = 0),
                ),
            ),
        )

        val section = customized.sections.single()
        assertTrue(section.freeform)
        // La grille place les cases par rangée puis colonne.
        assertEquals(listOf("light.a", "light.b"), section.tiles.map { it.entityId })
        val moved = section.tiles.first { it.entityId == "light.b" }
        assertEquals(2, moved.gridCol)
        assertEquals(1, moved.gridRow)
        assertEquals(2, moved.spanX)
    }

    @Test
    fun `widgets are placed on the same freeform grid`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(
                tiles = listOf(
                    PlacedTile(
                        id = "w1",
                        widget = WidgetItem("w1", WidgetKind.Clock, WidgetKind.CLOCK_MINIMAL),
                        col = 0,
                        row = 0,
                        spanX = 2,
                        spanY = 2,
                    ),
                ),
            ),
        )

        val widgetTile = customized.sections.single().tiles.single()
        assertEquals(TileCategory.Clock, widgetTile.category)
        assertEquals(WidgetKind.CLOCK_MINIMAL, widgetTile.widgetTheme)
        assertEquals(2, widgetTile.spanX)
        assertEquals(2, widgetTile.spanY)
    }

    @Test
    fun `custom label overrides the entity name`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(
                tiles = listOf(
                    PlacedTile(id = "tile_light.a", entityId = "light.a", label = "Lampe canapé"),
                ),
            ),
        )

        assertEquals("Lampe canapé", customized.sections.single().tiles.single().name)
    }

    @Test
    fun `stored label keeps only real renames`() {
        assertEquals("Enceinte salon", storedLabel("Enceinte salon", "Chambre speaker"))
        assertNull(storedLabel("Chambre speaker", "Chambre speaker"))
        assertNull(storedLabel("   ", "Chambre speaker"))
    }

    @Test
    fun `title widgets are dropped from existing dashboards`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(
                tiles = listOf(
                    PlacedTile(
                        id = "w1",
                        widget = WidgetItem(id = "w1", kind = WidgetKind.Title, text = "Salon"),
                    ),
                ),
            ),
        )

        // Plus aucune case texte : l'accueil généré reprend la main.
        assertEquals(model.sections, customized.sections)
    }

    @Test
    fun `scenes are kept after the custom grid`() {
        val scenes = HomeSectionUi("scenes", "Scènes", HomeSectionKind.Scenes)
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
                scenes,
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(tiles = listOf(PlacedTile(id = "tile_light.a", entityId = "light.a"))),
        )

        assertEquals(2, customized.sections.size)
        assertEquals(HomeSectionKind.Scenes, customized.sections.last().kind)
    }

    @Test
    fun `unknown entities are ignored`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomLayout(
            CustomDashboard(tiles = listOf(PlacedTile(id = "x", entityId = "light.disparue"))),
        )

        assertEquals(model, customized)
    }
}
