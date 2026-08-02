package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class StormsTest {

    private val manilaTileBounds = Bounds(latMin = 0.0, latMax = 30.0, lonMin = 120.0, lonMax = 180.0)

    data class Bounds(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double)

    @Test
    fun `keeps a storm inside the tile and drops one outside`() {
        val inside = Storm("wp012026", "Test-Inside", "TY", 950, lat = 14.6, lon = 130.0)
        val outside = Storm("ep072026", "Genevieve", "TS", 1006, lat = 24.5, lon = -138.4) // real NHC data, far East Pacific

        val result = Storms.withinBounds(
            listOf(inside, outside),
            manilaTileBounds.latMin, manilaTileBounds.latMax, manilaTileBounds.lonMin, manilaTileBounds.lonMax,
        )

        assertEquals(1, result.size)
        assertEquals("wp012026", result[0].id)
    }

    @Test
    fun `boundary values are inclusive`() {
        val onEdge = Storm("x", "Edge", "TS", null, lat = 30.0, lon = 120.0)
        val result = Storms.withinBounds(
            listOf(onEdge),
            manilaTileBounds.latMin, manilaTileBounds.latMax, manilaTileBounds.lonMin, manilaTileBounds.lonMax,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `empty list stays empty`() {
        val result = Storms.withinBounds(emptyList(), 0.0, 30.0, 120.0, 180.0)
        assertEquals(0, result.size)
    }
}
