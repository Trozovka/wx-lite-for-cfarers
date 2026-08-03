package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {

    @Test
    fun `formats Manila's coordinates in degrees-minutes`() {
        assertEquals("14-36.0 N", Coordinates.formatLat(14.6))
        assertEquals("121-00.0 E", Coordinates.formatLon(121.0))
    }

    @Test
    fun `formats southern and western hemispheres correctly`() {
        assertEquals("33-54.0 S", Coordinates.formatLat(-33.9))
        assertEquals("040-06.0 W", Coordinates.formatLon(-40.1))
    }

    @Test
    fun `degMinToDecimal is the exact inverse of decimalToDegMin`() {
        val original = 14.6
        val dm = Coordinates.decimalToDegMin(original)
        val roundTripped = Coordinates.degMinToDecimal(dm.degrees, dm.minutes, dm.positive)
        assertEquals(original, roundTripped, 0.0001)
    }

    @Test
    fun `degMinToDecimal round trip works for southern and western values too`() {
        val lat = -33.9
        val latDm = Coordinates.decimalToDegMin(lat)
        assertEquals(lat, Coordinates.degMinToDecimal(latDm.degrees, latDm.minutes, latDm.positive), 0.0001)

        val lon = -179.5
        val lonDm = Coordinates.decimalToDegMin(lon)
        assertEquals(lon, Coordinates.degMinToDecimal(lonDm.degrees, lonDm.minutes, lonDm.positive), 0.0001)
    }

    @Test
    fun `minutes never displays as 60_0, rolls into the next degree instead`() {
        // 0.999999 degrees is 0 deg, 59.99994 min -- must not format as "0-60.0 N".
        val formatted = Coordinates.formatLat(0.999999)
        assertEquals("01-00.0 N", formatted)
    }

    @Test
    fun `exact whole degrees format with zero minutes`() {
        assertEquals("00-00.0 N", Coordinates.formatLat(0.0))
        assertEquals("090-00.0 E", Coordinates.formatLon(90.0))
    }

    @Test
    fun `validates latitude degrees-minutes ranges`() {
        assertTrue(Coordinates.isValidLat(14, 36.0))
        assertTrue(Coordinates.isValidLat(90, 0.0))
        assertFalse(Coordinates.isValidLat(90, 0.1)) // 90 degrees exactly means minutes must be 0
        assertFalse(Coordinates.isValidLat(91, 0.0))
        assertFalse(Coordinates.isValidLat(14, 60.0))
        assertFalse(Coordinates.isValidLat(-1, 0.0))
    }

    @Test
    fun `validates longitude degrees-minutes ranges`() {
        assertTrue(Coordinates.isValidLon(121, 0.0))
        assertTrue(Coordinates.isValidLon(180, 0.0))
        assertFalse(Coordinates.isValidLon(180, 0.1))
        assertFalse(Coordinates.isValidLon(181, 0.0))
        assertFalse(Coordinates.isValidLon(121, 59.95))
    }

    @Test
    fun `ceilDegree and floorDegree bound a fractional range correctly`() {
        assertEquals(11, Coordinates.ceilDegree(10.2))
        assertEquals(10, Coordinates.floorDegree(10.2))
        assertEquals(-10, Coordinates.ceilDegree(-10.8))
        assertEquals(-11, Coordinates.floorDegree(-10.8))
    }
}
