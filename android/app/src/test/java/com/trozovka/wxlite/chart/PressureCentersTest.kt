package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureCentersTest {

    @Test
    fun `finds an obvious low pressure center in the middle`() {
        val grid = arrayOf(
            doubleArrayOf(1015.0, 1015.0, 1015.0),
            doubleArrayOf(1015.0, 970.0, 1015.0),
            doubleArrayOf(1015.0, 1015.0, 1015.0),
        )
        val centers = PressureCenters.find(grid, minProminenceHpa = 2.0)
        assertEquals(1, centers.size)
        assertEquals(CenterType.LOW, centers[0].type)
        assertEquals(1, centers[0].row)
        assertEquals(1, centers[0].col)
        assertEquals(970.0, centers[0].valueHpa, 0.001)
    }

    @Test
    fun `finds an obvious high pressure center`() {
        val grid = arrayOf(
            doubleArrayOf(1005.0, 1005.0, 1005.0),
            doubleArrayOf(1005.0, 1040.0, 1005.0),
            doubleArrayOf(1005.0, 1005.0, 1005.0),
        )
        val centers = PressureCenters.find(grid, minProminenceHpa = 2.0)
        assertEquals(1, centers.size)
        assertEquals(CenterType.HIGH, centers[0].type)
    }

    @Test
    fun `a flat grid has no centers`() {
        val grid = Array(4) { DoubleArray(4) { 1013.0 } }
        assertTrue(PressureCenters.find(grid).isEmpty())
    }

    @Test
    fun `a bump smaller than the prominence threshold is not flagged`() {
        val grid = arrayOf(
            doubleArrayOf(1013.0, 1013.0, 1013.0),
            doubleArrayOf(1013.0, 1013.5, 1013.0), // only 0.5 hPa above neighbors
            doubleArrayOf(1013.0, 1013.0, 1013.0),
        )
        assertTrue(PressureCenters.find(grid, minProminenceHpa = 2.0).isEmpty())
    }

    @Test
    fun `does not crash on a center at the grid edge`() {
        val grid = arrayOf(
            doubleArrayOf(960.0, 1015.0, 1015.0),
            doubleArrayOf(1015.0, 1015.0, 1015.0),
            doubleArrayOf(1015.0, 1015.0, 1015.0),
        )
        val centers = PressureCenters.find(grid, minProminenceHpa = 2.0)
        assertEquals(1, centers.size)
        assertEquals(CenterType.LOW, centers[0].type)
        assertEquals(0, centers[0].row)
        assertEquals(0, centers[0].col)
    }
}
