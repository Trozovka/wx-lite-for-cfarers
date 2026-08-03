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

    /** Every tile whose bounds overlap the given lat/lon box -- used to
     * sync a whole passage-plan area (typically 1-4 tiles) in one action,
     * rather than only the single tile under the crosshair. Does not
     * handle a box that crosses the +/-180 antimeridian (lonMin must be
     * <= lonMax); a passage plan spanning that line is a known
     * simplification, not handled here. */
    fun tilesIntersecting(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): List<String> {
        val result = mutableListOf<String>()
        var lat = LAT_RANGE.first
        while (lat < LAT_RANGE.last) {
            val tileLatMax = lat + TILE_LAT_SIZE
            if (tileLatMax > latMin && lat < latMax) {
                var lon = LON_RANGE.first
                while (lon < LON_RANGE.last) {
                    val tileLonMax = lon + TILE_LON_SIZE
                    if (tileLonMax > lonMin && lon < lonMax) {
                        result.add("lat${lat}_lon${lon}")
                    }
                    lon += TILE_LON_SIZE
                }
            }
            lat += TILE_LAT_SIZE
        }
        return result
    }
}
