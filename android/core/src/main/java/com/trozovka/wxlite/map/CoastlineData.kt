package com.trozovka.wxlite.map

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One closed polygon ring — a coastline/land boundary — as lat/lon pairs. */
data class LatLon(val lat: Double, val lon: Double)

data class CoastlinePolygon(val points: List<LatLon>)

/**
 * Parses the bundled coastline asset (Natural Earth's public-domain 110m
 * land data, packed by backend/pack_coastline.py). Static data, bundled
 * as an asset — never fetched over the network, no reason to spend the
 * bandwidth on data that never changes.
 */
object CoastlineData {
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'O'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte())

    fun parse(bytes: ByteArray): List<CoastlinePolygon> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buf.get(magic)
        require(magic.contentEquals(MAGIC)) { "Not a COST file (bad magic)" }

        val numPolygons = buf.int.toLong() and 0xFFFFFFFFL

        val polygons = ArrayList<CoastlinePolygon>(numPolygons.toInt())
        repeat(numPolygons.toInt()) {
            val numPoints = buf.short.toInt() and 0xFFFF
            val points = ArrayList<LatLon>(numPoints)
            repeat(numPoints) {
                val latX100 = buf.short.toInt() // signed, no masking needed
                val lonX100 = buf.short.toInt()
                points.add(LatLon(latX100 / 100.0, lonX100 / 100.0))
            }
            polygons.add(CoastlinePolygon(points))
        }

        return polygons
    }
}
