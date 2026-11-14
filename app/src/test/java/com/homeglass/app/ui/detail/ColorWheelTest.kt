package com.homeglass.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorWheelTest {

    @Test
    fun `hsv conversion covers the primary hues`() {
        assertEquals(Triple(255, 0, 0), hsvToRgb(0f, 1f, 1f))
        assertEquals(Triple(255, 255, 0), hsvToRgb(60f, 1f, 1f))
        assertEquals(Triple(0, 255, 0), hsvToRgb(120f, 1f, 1f))
        assertEquals(Triple(0, 255, 255), hsvToRgb(180f, 1f, 1f))
        assertEquals(Triple(0, 0, 255), hsvToRgb(240f, 1f, 1f))
        assertEquals(Triple(255, 0, 255), hsvToRgb(300f, 1f, 1f))
    }

    @Test
    fun `hue wraps around and saturation zero is white`() {
        assertEquals(hsvToRgb(0f, 1f, 1f), hsvToRgb(360f, 1f, 1f))
        assertEquals(Triple(255, 255, 255), hsvToRgb(200f, 0f, 1f))
    }

    @Test
    fun `disc center is white and the edge is fully saturated`() {
        val center = wheelPick(0f, 0f, radius = 100f)
        assertEquals(0f, center.saturation, 0.0001f)
        assertEquals(Triple(255, 255, 255), Triple(center.red, center.green, center.blue))

        val edge = wheelPick(offsetX = 100f, offsetY = 0f, radius = 100f)
        assertEquals(0f, edge.hue, 0.01f)
        assertEquals(1f, edge.saturation, 0.0001f)
        assertEquals(Triple(255, 0, 0), Triple(edge.red, edge.green, edge.blue))
    }

    @Test
    fun `points outside the disc stay saturated instead of overflowing`() {
        val far = wheelPick(offsetX = 400f, offsetY = 0f, radius = 100f)
        assertEquals(1f, far.saturation, 0.0001f)
        assertEquals(255, far.red)
    }

    @Test
    fun `disc angles follow the screen orientation`() {
        val bottom = wheelPick(0f, 100f, radius = 100f)
        assertEquals(90f, bottom.hue, 0.01f)

        val left = wheelPick(-100f, 0f, radius = 100f)
        assertEquals(180f, left.hue, 0.01f)
    }

    @Test
    fun `a colour is placed back at its angle and saturation`() {
        val (x, y) = wheelPosition(red = 255, green = 0, blue = 0)
        assertEquals(1f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)

        val (centreX, centreY) = wheelPosition(255, 255, 255)
        assertEquals(0f, centreX, 0.0001f)
        assertEquals(0f, centreY, 0.0001f)

        // Vert ≈ 120° sur le disque (vers le bas-gauche en coordonnées d'écran).
        val (greenX, greenY) = wheelPosition(0, 255, 0)
        assertEquals(-0.5f, greenX, 0.001f)
        assertTrue(greenY > 0.86f)
    }
}
