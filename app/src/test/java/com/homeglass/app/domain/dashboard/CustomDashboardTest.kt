package com.homeglass.app.domain.dashboard

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDashboardTest {

    @Test
    fun `an empty dashboard is not configured`() {
        assertFalse(CustomDashboard().isConfigured)
        assertFalse(CustomDashboard().hasContent)
    }

    @Test
    fun `title tiles are removed while other tiles stay`() {
        val dashboard = CustomDashboard(
            tiles = listOf(
                PlacedTile(id = "a", entityId = "light.salon", col = 1, row = 2, spanX = 2, spanY = 2),
                PlacedTile(id = "w", widget = WidgetItem("w", WidgetKind.Title, text = "Salon")),
            ),
        ).normalized()

        assertEquals(listOf("a"), dashboard.tiles.map { it.id })
        assertEquals(2, dashboard.tiles.single().spanY)
    }

    @Test
    fun `placed tiles keep their geometry and order`() {
        val dashboard = CustomDashboard(
            tiles = listOf(
                PlacedTile(id = "a", entityId = "light.salon", col = 0, row = 0, spanX = 2, spanY = 1),
                PlacedTile(id = "b", entityId = "light.chambre", col = 2, row = 0),
            ),
        )

        assertTrue(dashboard.isConfigured)
        val normalized = dashboard.normalized()
        assertEquals(listOf("a", "b"), normalized.tiles.map { it.id })
        assertEquals(2, normalized.tiles.first().spanX)
    }

    @Test
    fun `normalization clamps geometry and drops invalid tiles`() {
        val dashboard = CustomDashboard(
            tiles = listOf(
                PlacedTile(id = "a", entityId = "light.salon", col = -3, row = -1, spanX = 0, spanY = 0),
                PlacedTile(id = "b"),
                PlacedTile(id = "a", entityId = "light.doublon"),
            ),
        ).normalized()

        assertEquals(listOf("a"), dashboard.tiles.map { it.id })
        val tile = dashboard.tiles.single()
        assertEquals(0, tile.col)
        assertEquals(0, tile.row)
        assertEquals(1, tile.spanX)
        assertEquals(1, tile.spanY)
    }

    @Test
    fun `legacy zones migrate into placed tiles without overlap`() {
        val dashboard = CustomDashboard(
            sections = listOf(
                CustomSection(id = "zone", title = "Salon", entityIds = listOf("light.a", "light.b", "light.c")),
            ),
        ).normalized()

        assertEquals(3, dashboard.tiles.size)
        assertEquals(setOf("light.a", "light.b", "light.c"), dashboard.tiles.map { it.entityId }.toSet())
        assertNoOverlap(dashboard.tiles)
        assertTrue(dashboard.sections.isEmpty())
        assertTrue(dashboard.tiles.all { it.col >= 0 && it.row >= 0 })
    }

    @Test
    fun `legacy widgets migrate into placed tiles with their theme`() {
        val dashboard = CustomDashboard(
            widgets = listOf(WidgetItem(id = "w1", kind = WidgetKind.Clock, theme = "inconnu")),
        ).normalized()

        val tile = dashboard.tiles.single()
        assertEquals("w1", tile.id)
        assertEquals(WidgetKind.CLOCK_CLASSIC, tile.widget?.theme)
        assertTrue(dashboard.widgets.isEmpty())
    }

    @Test
    fun `settle moves the overlapped tile to a free cell`() {
        val tiles = listOf(
            PlacedTile(id = "a", entityId = "light.a", col = 0, row = 0),
            PlacedTile(id = "b", entityId = "light.b", col = 0, row = 0),
        )

        val settled = DashboardLayout.settle(tiles, priorityId = "b", columns = 4)

        assertEquals(0 to 0, settled.first { it.id == "b" }.let { it.col to it.row })
        assertNoOverlap(settled)
    }

    @Test
    fun `auto place packs wide tiles on the grid`() {
        val placed = DashboardLayout.autoPlace(
            listOf(
                PlacedTile(id = "a", entityId = "light.a", spanX = 2),
                PlacedTile(id = "b", entityId = "light.b", spanX = 2),
                PlacedTile(id = "c", entityId = "light.c", spanX = 2),
            ),
            columns = 4,
        )

        assertNoOverlap(placed)
        assertTrue(placed.all { it.col + it.spanX <= 4 })
    }

    @Test
    fun `dashboard round trips through json`() {
        val dashboard = CustomDashboard(
            tiles = listOf(
                PlacedTile(id = "w1", widget = WidgetItem("w1", WidgetKind.Calendar, WidgetKind.CALENDAR_FOCUS), col = 1, row = 2),
            ),
        )

        val raw = Json.encodeToString(CustomDashboard.serializer(), dashboard)
        val decoded = Json.decodeFromString(CustomDashboard.serializer(), raw)

        assertEquals(dashboard, decoded)
    }

    private fun assertNoOverlap(tiles: List<PlacedTile>) {
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                assertFalse(
                    "chevauchement entre ${tiles[i].id} et ${tiles[j].id}",
                    DashboardLayout.overlaps(tiles[i], tiles[j]),
                )
            }
        }
    }
}
