package com.trozovka.wxlite.chart

enum class CenterType { HIGH, LOW }

data class PressureCenter(val row: Int, val col: Int, val valueHpa: Double, val type: CenterType)

/**
 * Finds closed High/Low centers: a grid point strictly higher (or lower)
 * than all 8 of its neighbors. A minimum prominence is required so flat
 * noise in near-uniform regions doesn't get flagged as a "center" —
 * matches how real weatherfax charts only mark genuine closed systems,
 * not every minor wiggle.
 */
object PressureCenters {
    fun find(grid: Array<DoubleArray>, minProminenceHpa: Double = 2.0): List<PressureCenter> {
        val nRows = grid.size
        if (nRows == 0) return emptyList()
        val nCols = grid[0].size
        if (nCols == 0) return emptyList()

        val centers = mutableListOf<PressureCenter>()

        for (r in 0 until nRows) {
            for (c in 0 until nCols) {
                val value = grid[r][c]
                var isMax = true
                var isMin = true
                var closestNeighborDiffForMax = Double.MAX_VALUE
                var closestNeighborDiffForMin = Double.MAX_VALUE

                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr < 0 || nr >= nRows || nc < 0 || nc >= nCols) continue

                        val neighbor = grid[nr][nc]
                        if (neighbor >= value) isMax = false
                        if (neighbor <= value) isMin = false
                        closestNeighborDiffForMax = minOf(closestNeighborDiffForMax, value - neighbor)
                        closestNeighborDiffForMin = minOf(closestNeighborDiffForMin, neighbor - value)
                    }
                }

                if (isMax && closestNeighborDiffForMax >= minProminenceHpa) {
                    centers.add(PressureCenter(r, c, value, CenterType.HIGH))
                } else if (isMin && closestNeighborDiffForMin >= minProminenceHpa) {
                    centers.add(PressureCenter(r, c, value, CenterType.LOW))
                }
            }
        }

        return centers
    }
}
