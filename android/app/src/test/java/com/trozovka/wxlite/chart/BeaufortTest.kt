package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class BeaufortTest {

    @Test
    fun `calm wind is force 0`() {
        assertEquals(0, Beaufort.forceForKnots(0.0))
    }

    @Test
    fun `each standard force band maps to its expected force number`() {
        assertEquals(1, Beaufort.forceForKnots(2.0)) // light air, 1-3kt
        assertEquals(2, Beaufort.forceForKnots(5.0)) // light breeze, 4-6kt
        assertEquals(3, Beaufort.forceForKnots(8.0)) // gentle breeze, 7-10kt
        assertEquals(4, Beaufort.forceForKnots(13.0)) // moderate breeze, 11-16kt
        assertEquals(5, Beaufort.forceForKnots(19.0)) // fresh breeze, 17-21kt
        assertEquals(6, Beaufort.forceForKnots(25.0)) // strong breeze, 22-27kt
        assertEquals(7, Beaufort.forceForKnots(30.0)) // near gale, 28-33kt
        assertEquals(8, Beaufort.forceForKnots(37.0)) // gale, 34-40kt
        assertEquals(9, Beaufort.forceForKnots(44.0)) // strong gale, 41-47kt
        assertEquals(10, Beaufort.forceForKnots(50.0)) // storm, 48-55kt
        assertEquals(11, Beaufort.forceForKnots(60.0)) // violent storm, 56-63kt
        assertEquals(12, Beaufort.forceForKnots(70.0)) // hurricane, 64kt+
    }

    @Test
    fun `a very high speed still returns force 12, not an out-of-range value`() {
        assertEquals(12, Beaufort.forceForKnots(200.0))
    }

    @Test
    fun `negative input is treated as calm rather than throwing`() {
        assertEquals(0, Beaufort.forceForKnots(-5.0))
    }
}
