package com.trozovka.wxlite.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeProjectionTest {

    private val radius = 100.0

    @Test
    fun `the center of the visible hemisphere projects to the globe's own center`() {
        val p = GlobeProjection.project(latDeg = 0.0, lonDeg = 50.0, centerLonDeg = 50.0, radius)
        assertNotNull(p)
        assertEquals(0.0, p!!.x, 0.001)
        assertEquals(0.0, p.y, 0.001)
    }

    @Test
    fun `a point 30 degrees east of center projects to R times sin(30) on the x axis`() {
        val p = GlobeProjection.project(latDeg = 0.0, lonDeg = 30.0, centerLonDeg = 0.0, radius)
        assertNotNull(p)
        assertEquals(radius * 0.5, p!!.x, 0.01) // sin(30deg) = 0.5
        assertEquals(0.0, p.y, 0.01)
    }

    @Test
    fun `the north pole projects above center on screen (negative y), regardless of longitude`() {
        val p = GlobeProjection.project(latDeg = 90.0, lonDeg = 123.0, centerLonDeg = 0.0, radius)
        assertNotNull(p)
        assertEquals(0.0, p!!.x, 0.01)
        assertEquals(-radius, p.y, 0.01)
    }

    @Test
    fun `the south pole projects below center on screen (positive y)`() {
        val p = GlobeProjection.project(latDeg = -90.0, lonDeg = 0.0, centerLonDeg = 0.0, radius)
        assertNotNull(p)
        assertEquals(radius, p!!.y, 0.01)
    }

    @Test
    fun `the antipodal point is on the far side and not visible`() {
        val p = GlobeProjection.project(latDeg = 0.0, lonDeg = 180.0, centerLonDeg = 0.0, radius)
        assertNull(p)
    }

    @Test
    fun `a point well past the visible edge is not visible`() {
        val p = GlobeProjection.project(latDeg = 10.0, lonDeg = 170.0, centerLonDeg = 0.0, radius)
        assertNull(p)
    }

    @Test
    fun `a point near but inside the visible edge is visible`() {
        val p = GlobeProjection.project(latDeg = 0.0, lonDeg = 80.0, centerLonDeg = 0.0, radius)
        assertNotNull(p)
    }
}
