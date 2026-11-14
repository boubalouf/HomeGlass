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
        assertFalse(CustomDashboard(listOf(CustomSection(id = "zone", entityIds = emptyList()))).isConfigured)
    }

    @Test
    fun `sections keep their titles and order`() {
        val dashboard = CustomDashboard(
            listOf(
                CustomSection(id = "a", title = "Salon", entityIds = listOf("light.salon")),
                CustomSection(id = "b", title = "Chambre", entityIds = listOf("light.chambre")),
            ),
        )

        assertTrue(dashboard.isConfigured)
        assertEquals(listOf("Salon", "Chambre"), dashboard.normalized().sections.map { it.title })
    }

    @Test
    fun `normalization drops empty sections blanks and duplicates`() {
        val dashboard = CustomDashboard(
            listOf(
                CustomSection(id = "a", title = "  Salon  ", entityIds = listOf("light.salon", "light.salon")),
                CustomSection(id = "b", title = "Zone vide", entityIds = emptyList()),
                CustomSection(id = "c", title = "", entityIds = listOf("light.salon", " ", "light.cuisine")),
            ),
        ).normalized()

        assertEquals(listOf("a", "c"), dashboard.sections.map { it.id })
        assertEquals("Salon", dashboard.sections.first().title)
        // Une entité n'apparaît que dans sa première zone.
        assertEquals(listOf("light.salon"), dashboard.sections.first().entityIds)
        assertEquals(listOf("light.cuisine"), dashboard.sections.last().entityIds)
    }

    @Test
    fun `legacy list migrates into a single unnamed section`() {
        val migrated = CustomDashboard.single(listOf("light.salon", "switch.bureau"))

        assertEquals(1, migrated.sections.size)
        assertEquals("", migrated.sections.single().title)
        assertTrue(migrated.isConfigured)
    }

    @Test
    fun `dashboard round trips through json`() {
        val dashboard = CustomDashboard(
            listOf(CustomSection(id = "a", title = "Salon", entityIds = listOf("light.salon"))),
        )

        val raw = Json.encodeToString(CustomDashboard.serializer(), dashboard)
        val decoded = Json.decodeFromString(CustomDashboard.serializer(), raw)

        assertEquals(dashboard, decoded)
    }
}
