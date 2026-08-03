package com.trozovka.wxlite.data

import kotlin.math.floor

/**
 * Must match backend/tiles.py's grid exactly — same tile IDs, same
 * boundaries — or the app requests a tile the backend never generated.
 *
 * Ported carefully around one real gotcha: Python's `%` is floored
 * (always non-negative for a positive divisor), Kotlin/Java's `%` is
 * truncated (sign follows the dividend) — they disagree for negative
 * longitudes, which is exactly the input this function has to handle
 * correctly (western hemisphere, and the +/-180 wraparound).
 */
object Tiles {
    const val TILE_LAT_SIZE = 30
    const val TILE_LON_SIZE = 60
    val LAT_RANGE = -60..60
    val LON_RANGE = -180..180

    /** Python-compatible floored modulo (b must be positive). */
    private fun floorMod(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    private fun floorDiv(a: Double, b: Double): Int = floor(a / b).toInt()

    fun tileForPosition(lat: Double, lon: Double): String? {
        val normalizedLon = floorMod(lon + 180.0, 360.0) - 180.0

        if (lat < LAT_RANGE.first || lat >= LAT_RANGE.last) return null

        val tileLat = LAT_RANGE.first + floorDiv(lat - LAT_RANGE.first, TILE_LAT_SIZE.toDouble()) * TILE_LAT_SIZE
        val tileLon = LON_RANGE.first + floorDiv(normalizedLon - LON_RANGE.first, TILE_LON_SIZE.toDouble()) * TILE_LON_SIZE

        return "lat${tileLat}_lon${tileLon}"
    }

    /** Distinct tiles actually containing each of the given lat/lon
     * points, in order of first occurrence -- deliberately NOT the
     * centroid (the average of a spread-out set of points can land
     * nowhere near any of them, in open ocean) and NOT every tile a
     * bounding box touches (pulls in tiles the area barely grazes).
     * Nearby points naturally collapse onto the same tile via distinct(). */
    fun tilesFor(points: List<Pair<Double, Double>>): List<String> =
        points.mapNotNull { (lat, lon) -> tileForPosition(lat, lon) }.distinct()
}
