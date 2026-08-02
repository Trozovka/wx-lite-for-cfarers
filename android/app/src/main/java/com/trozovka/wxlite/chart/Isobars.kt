package com.trozovka.wxlite.chart

import kotlin.math.ceil

/** Position in grid-space: row/col are fractional (interpolated between grid points). */
data class GridPoint2D(val row: Double, val col: Double)

data class IsobarSegment(val levelHpa: Double, val p1: GridPoint2D, val p2: GridPoint2D)

/**
 * Marching squares over a pressure grid, producing contour line segments at
 * a fixed interval (4 hPa is the real weatherfax convention). Grid is
 * row-major, grid[row][col], matching WxlFile's point layout.
 */
object Isobars {
    fun generate(grid: Array<DoubleArray>, intervalHpa: Double = 4.0): List<IsobarSegment> {
        if (grid.isEmpty() || grid[0].isEmpty()) return emptyList()

        var minVal = Double.MAX_VALUE
        var maxVal = -Double.MAX_VALUE
        for (row in grid) {
            for (v in row) {
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
        }

        val segments = mutableListOf<IsobarSegment>()
        var level = ceil(minVal / intervalHpa) * intervalHpa
        while (level <= maxVal) {
            segments.addAll(segmentsForLevel(grid, level))
            level += intervalHpa
        }
        return segments
    }

    private fun segmentsForLevel(grid: Array<DoubleArray>, level: Double): List<IsobarSegment> {
        val segments = mutableListOf<IsobarSegment>()
        val nRows = grid.size
        val nCols = grid[0].size

        for (r in 0 until nRows - 1) {
            for (c in 0 until nCols - 1) {
                val tl = grid[r][c]
                val tr = grid[r][c + 1]
                val br = grid[r + 1][c + 1]
                val bl = grid[r + 1][c]

                var caseIndex = 0
                if (tl > level) caseIndex = caseIndex or 8
                if (tr > level) caseIndex = caseIndex or 4
                if (br > level) caseIndex = caseIndex or 2
                if (bl > level) caseIndex = caseIndex or 1

                if (caseIndex == 0 || caseIndex == 15) continue

                fun lerp(v1: Double, v2: Double) = (level - v1) / (v2 - v1)

                val top = GridPoint2D(r.toDouble(), c + lerp(tl, tr))
                val right = GridPoint2D(r + lerp(tr, br), (c + 1).toDouble())
                val bottom = GridPoint2D((r + 1).toDouble(), c + lerp(bl, br))
                val left = GridPoint2D(r + lerp(tl, bl), c.toDouble())

                // Cases k and (15-k) are geometrically identical for a
                // contour LINE (as opposed to a filled region) — flipping
                // which corners are "above" doesn't move the boundary.
                // 5 and 10 are the ambiguous saddle cases; both resolved
                // the same way here for consistency.
                when (caseIndex) {
                    1, 14 -> segments.add(IsobarSegment(level, left, bottom))
                    2, 13 -> segments.add(IsobarSegment(level, bottom, right))
                    3, 12 -> segments.add(IsobarSegment(level, left, right))
                    4, 11 -> segments.add(IsobarSegment(level, top, right))
                    6, 9 -> segments.add(IsobarSegment(level, top, bottom))
                    7, 8 -> segments.add(IsobarSegment(level, left, top))
                    5, 10 -> {
                        segments.add(IsobarSegment(level, left, top))
                        segments.add(IsobarSegment(level, bottom, right))
                    }
                }
            }
        }
        return segments
    }
}
