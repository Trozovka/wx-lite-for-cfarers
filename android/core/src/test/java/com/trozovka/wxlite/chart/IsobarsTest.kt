package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsobarsTest {

    @Test
    fun `a flat grid produces no contours`() {
        val grid = arrayOf(
            doubleArrayOf(1013.0, 1013.0),
            doubleArrayOf(1013.0, 1013.0),
        )
        assertTrue(Isobars.generate(grid, intervalHpa = 4.0).isEmpty())
    }

    @Test
    fun `single cell crossing produces the hand-computed segment endpoints`() {
        // tl=1000  tr=1010
        // bl=1005  br=1015
        // Contour level 1008 crosses the top edge (tl-tr) and bottom edge
        // (bl-br) — hand-computed via linear interpolation:
        //   top:    (1008-1000)/(1010-1000) = 0.8  -> (row 0, col 0.8)
        //   bottom: (1008-1005)/(1015-1005) = 0.3  -> (row 1, col 0.3)
        val grid = arrayOf(
            doubleArrayOf(1000.0, 1010.0),
            doubleArrayOf(1005.0, 1015.0),
        )
        val allSegments = Isobars.generate(grid, intervalHpa = 4.0)
        val at1008 = allSegments.filter { it.levelHpa == 1008.0 }
        assertEquals(1, at1008.size)
        val seg = at1008[0]

        val top = GridPoint2D(0.0, 0.8)
        val bottom = GridPoint2D(1.0, 0.3)
        val matchesForward = closeTo(seg.p1, top) && closeTo(seg.p2, bottom)
        val matchesReverse = closeTo(seg.p1, bottom) && closeTo(seg.p2, top)
        assertTrue("segment endpoints didn't match hand-computed values: $seg", matchesForward || matchesReverse)
    }

    @Test
    fun `higher interval count for a steeper gradient`() {
        // A steeper pressure range should cross more 4hPa levels than a
        // shallow one over the same grid size.
        val shallow = arrayOf(
            doubleArrayOf(1010.0, 1011.0),
            doubleArrayOf(1010.0, 1011.0),
        )
        val steep = arrayOf(
            doubleArrayOf(980.0, 1040.0),
            doubleArrayOf(980.0, 1040.0),
        )
        val shallowLevels = Isobars.generate(shallow, intervalHpa = 4.0).map { it.levelHpa }.distinct()
        val steepLevels = Isobars.generate(steep, intervalHpa = 4.0).map { it.levelHpa }.distinct()
        assertTrue(steepLevels.size > shallowLevels.size)
    }

    private fun closeTo(a: GridPoint2D, b: GridPoint2D, eps: Double = 1e-9) =
        Math.abs(a.row - b.row) < eps && Math.abs(a.col - b.col) < eps
}
