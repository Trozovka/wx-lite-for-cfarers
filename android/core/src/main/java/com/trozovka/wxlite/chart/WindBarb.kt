package com.trozovka.wxlite.chart

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Standard (WMO) meteorological wind barb: direction is where the wind is
 * coming FROM (not blowing toward), speed is rounded to the nearest 5 knots
 * for symbol purposes, and the symbol is built from as few pieces as
 * possible: each pennant (flag) = 50kt, each full barb = 10kt, one half
 * barb = 5kt. Below 3kt is drawn as a calm circle, no barbs.
 */
data class WindBarbSymbol(
    /** Rounded to the nearest 5kt -- correct for the barb SYMBOL (which is
     * only ever drawn in 5kt increments by convention), but NOT what
     * Beaufort force should be classified from -- see [trueSpeedKnots]. */
    val speedKnots: Int,
    /** Unrounded speed in knots -- Beaufort.forceForKnots must use this,
     * not [speedKnots]. Rounding to the nearest 5kt before classifying
     * can shift the result by a whole Beaufort force near a boundary
     * (e.g. a true 16.6kt wind is force 5, but rounds to 15kt first,
     * which reads back as force 4) -- confirmed as a real bug, not
     * theoretical, while auditing this calculation for accuracy. */
    val trueSpeedKnots: Double,
    val fromDirectionDeg: Double,
    val pennants: Int,
    val fullBarbs: Int,
    val halfBarbs: Int,
    val isCalm: Boolean,
)

object WindBarb {
    private const val MS_TO_KNOTS = 1.943844

    fun fromComponents(uMetersPerSecond: Double, vMetersPerSecond: Double): WindBarbSymbol {
        val speedMs = sqrt(uMetersPerSecond * uMetersPerSecond + vMetersPerSecond * vMetersPerSecond)
        val speedKnots = speedMs * MS_TO_KNOTS
        val roundedKnots = (Math.round(speedKnots / 5.0) * 5).toInt()

        // Compass bearing (clockwise from north) of the vector pointing back
        // toward where the wind originated: atan2(dx, dy) with dx=-u, dy=-v
        // — note the swapped argument order vs standard math atan2(y, x),
        // which is what converts "counterclockwise from east" into
        // "clockwise from north".
        val fromDirection = (Math.toDegrees(atan2(-uMetersPerSecond, -vMetersPerSecond)) + 360.0) % 360.0

        if (roundedKnots < 3) {
            return WindBarbSymbol(0, speedKnots, fromDirection, 0, 0, 0, isCalm = true)
        }

        var remaining = roundedKnots
        val pennants = remaining / 50
        remaining %= 50
        val fullBarbs = remaining / 10
        remaining %= 10
        val halfBarbs = if (remaining >= 5) 1 else 0

        return WindBarbSymbol(roundedKnots, speedKnots, fromDirection, pennants, fullBarbs, halfBarbs, isCalm = false)
    }
}
