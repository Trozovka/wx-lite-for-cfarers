package com.trozovka.wxlite.chart

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/** A low-pressure center's current position and the bearing it's heading
 * toward, based on where the matching low is found at the next cached
 * forecast hour. */
data class TrackedLow(val lat: Double, val lon: Double, val bearingDeg: Double)

/**
 * Matches each low-pressure center at one forecast hour to its likely
 * position at another hour (nearest low within a plausible travel
 * distance), and computes the bearing between them — the basis for the
 * "which way is the low moving" arrow. Deliberately simple nearest-
 * neighbor matching, not a real storm-tracking algorithm: adequate for a
 * single system moving a few hundred km between two forecast steps a few
 * hours apart, which is the actual scale this is used at.
 */
object LowPressureTracker {
    fun track(
        fromLows: List<Pair<Double, Double>>,
        toLows: List<Pair<Double, Double>>,
        maxDistanceDeg: Double = 15.0,
    ): List<TrackedLow> {
        if (toLows.isEmpty()) return emptyList()

        val result = mutableListOf<TrackedLow>()
        for ((fLat, fLon) in fromLows) {
            val nearest = toLows.minByOrNull { (tLat, tLon) -> distanceDeg(fLat, fLon, tLat, tLon) } ?: continue
            val (nLat, nLon) = nearest
            if (distanceDeg(fLat, fLon, nLat, nLon) <= maxDistanceDeg) {
                result.add(TrackedLow(fLat, fLon, bearingDeg(fLat, fLon, nLat, nLon)))
            }
        }
        return result
    }

    private fun distanceDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** Equirectangular approximation (clockwise from north) -- adequate at
     * the regional scale a single low moves between two forecast steps,
     * consistent with the same-precision approximation already used for
     * wind direction elsewhere in this app; not a great-circle formula. */
    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val avgLatRad = Math.toRadians((lat1 + lat2) / 2.0)
        val dx = (lon2 - lon1) * cos(avgLatRad)
        val dy = lat2 - lat1
        return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }
}
