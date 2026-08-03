package com.trozovka.wxlite.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapTransformTest {

    @Test
    fun `worldToScreen applies scale then translate`() {
        val t = MapTransform(scale = 2.0, translateX = 100.0, translateY = 50.0)
        val (sx, sy) = t.worldToScreen(10.0, 5.0)
        assertEquals(120.0, sx, 0.001) // 10*2 + 100
        assertEquals(60.0, sy, 0.001) // 5*2 + 50
    }

    @Test
    fun `screenToWorld is the exact inverse of worldToScreen`() {
        val t = MapTransform(scale = 3.5, translateX = -40.0, translateY = 220.0)
        val (sx, sy) = t.worldToScreen(17.0, -8.0)
        val (wx, wy) = t.screenToWorld(sx, sy)
        assertEquals(17.0, wx, 0.0001)
        assertEquals(-8.0, wy, 0.0001)
    }

    @Test
    fun `panned shifts translate by the given screen-pixel delta and leaves scale untouched`() {
        val t = MapTransform(scale = 5.0, translateX = 10.0, translateY = 20.0)
        val panned = t.panned(dx = -30.0, dy = 15.0)
        assertEquals(-20.0, panned.translateX, 0.001)
        assertEquals(35.0, panned.translateY, 0.001)
        assertEquals(5.0, panned.scale, 0.001)
    }

    @Test
    fun `zoomed keeps the world point under the focal point fixed on screen`() {
        val t = MapTransform(scale = 1.0, translateX = 0.0, translateY = 0.0)
        val focusX = 100.0
        val focusY = 200.0
        val (worldUnderFocusBefore) = t.screenToWorld(focusX, focusY).let { listOf(it) }

        val zoomed = t.zoomed(factor = 2.0, focusX = focusX, focusY = focusY, minScale = 0.1, maxScale = 100.0)
        val worldUnderFocusAfter = zoomed.screenToWorld(focusX, focusY)

        assertEquals(worldUnderFocusBefore.first, worldUnderFocusAfter.first, 0.001)
        assertEquals(worldUnderFocusBefore.second, worldUnderFocusAfter.second, 0.001)
        assertEquals(2.0, zoomed.scale, 0.001)
    }

    @Test
    fun `zoomed clamps to maxScale and does not overshoot`() {
        val t = MapTransform(scale = 40.0, translateX = 0.0, translateY = 0.0)
        val zoomed = t.zoomed(factor = 3.0, focusX = 0.0, focusY = 0.0, minScale = 1.0, maxScale = 50.0)
        assertEquals(50.0, zoomed.scale, 0.001)
    }

    @Test
    fun `zoomed clamps to minScale and does not undershoot`() {
        val t = MapTransform(scale = 2.0, translateX = 0.0, translateY = 0.0)
        val zoomed = t.zoomed(factor = 0.1, focusX = 0.0, focusY = 0.0, minScale = 1.0, maxScale = 50.0)
        assertEquals(1.0, zoomed.scale, 0.001)
    }

    @Test
    fun `repeated small pans compose additively, same as one large pan`() {
        val t = MapTransform(scale = 1.0, translateX = 0.0, translateY = 0.0)
        val stepped = t.panned(5.0, 5.0).panned(5.0, 5.0).panned(5.0, 5.0)
        val direct = t.panned(15.0, 15.0)
        assertEquals(direct.translateX, stepped.translateX, 0.0001)
        assertEquals(direct.translateY, stepped.translateY, 0.0001)
    }
}
