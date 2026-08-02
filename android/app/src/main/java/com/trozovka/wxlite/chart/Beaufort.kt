package com.trozovka.wxlite.chart

/**
 * Standard WMO Beaufort wind force scale (0-12), by mean wind speed in
 * knots. Static lookup table, no external dependency.
 */
object Beaufort {
    // Inclusive upper bound (knots) for forces 0..11, in order; anything
    // above the last entry is force 12.
    private val upperBoundKnots = doubleArrayOf(1.0, 3.0, 6.0, 10.0, 16.0, 21.0, 27.0, 33.0, 40.0, 47.0, 55.0, 63.0)

    fun forceForKnots(knots: Double): Int {
        val speed = knots.coerceAtLeast(0.0)
        for (force in upperBoundKnots.indices) {
            if (speed <= upperBoundKnots[force]) return force
        }
        return 12
    }
}
