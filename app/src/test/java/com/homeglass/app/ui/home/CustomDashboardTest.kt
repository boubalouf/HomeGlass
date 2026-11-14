package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.domain.dashboard.CustomSection
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileUiModel
import org.junit.Assert.assertEquals
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

    private fun dashboard(vararg sections: CustomSection) = CustomDashboard(sections.toList())

    @Test
    fun `empty dashboard keeps the generated dashboard`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "Lumières", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        assertEquals(model, model.withCustomSections(CustomDashboard()))
    }

    @Test
    fun `zones filter order and title the tiles but keep scenes`() {
        val scenes = HomeSectionUi("scenes", "Scènes", HomeSectionKind.Scenes)
        val model = home(
            listOf(
                HomeSectionUi(
                    "a",
                    "Lumières",
                    HomeSectionKind.Tiles,
                    tiles = listOf(tile("light.a"), tile("light.b"), tile("light.c")),
                ),
                scenes,
            ),
        )

        val customized = model.withCustomSections(
            dashboard(
                CustomSection(id = "zone_salon", title = "Salon", entityIds = listOf("light.c", "light.a")),
                CustomSection(id = "zone_cuisine", title = "Cuisine", entityIds = listOf("light.b")),
            ),
        )

        assertEquals(3, customized.sections.size)
        assertEquals(listOf("Salon", "Cuisine"), customized.sections.dropLast(1).map { it.title })
        assertEquals(
            listOf("light.c", "light.a"),
            customized.sections.first().tiles.map { it.entityId },
        )
        assertEquals(listOf("light.b"), customized.sections[1].tiles.map { it.entityId })
        assertEquals(HomeSectionKind.Scenes, customized.sections.last().kind)
    }

    @Test
    fun `tiles from the rooms fill the gap`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomSections(
            dashboard(CustomSection(id = "zone", entityIds = listOf("light.room"))),
            extraTiles = listOf(tile("light.room")),
        )

        assertEquals(
            listOf("light.room"),
            customized.sections.first().tiles.map { it.entityId },
        )
    }

    @Test
    fun `unknown entities are ignored`() {
        val model = home(
            listOf(
                HomeSectionUi("a", "", HomeSectionKind.Tiles, tiles = listOf(tile("light.a"))),
            ),
        )

        val customized = model.withCustomSections(
            dashboard(CustomSection(id = "zone", entityIds = listOf("light.disparue"))),
        )

        assertEquals(model, customized)
    }
}
