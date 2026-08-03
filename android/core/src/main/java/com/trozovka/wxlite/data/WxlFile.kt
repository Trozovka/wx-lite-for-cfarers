package com.trozovka.wxlite.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses the app's compact weather format, produced by the backend's
 * pack.py. Layout (little-endian) — must stay byte-for-byte identical
 * to what pack.py writes:
 *
 *   4 bytes  magic       "WXL1"
 *   4 bytes  lat_min     float32
 *   4 bytes  lat_max     float32
 *   4 bytes  lon_min     float32
 *   4 bytes  lon_max     float32
 *   2 bytes  n_lat       uint16
 *   2 bytes  n_lon       uint16
 *   4 bytes  valid_time  uint32, unix seconds
 *   then n_lat*n_lon grid points, row-major from (lat_min, lon_min):
 *     2 bytes  pressure     int16, hPa * 10
 *     1 byte   temperature  int8, whole degrees C
 *     1 byte   wind_u       int8, m/s
 *     1 byte   wind_v       int8, m/s
 */
data class GridPoint(
    val pressureHpa: Float,
    val tempC: Int,
    val windU: Int,
    val windV: Int,
)

data class WxlFile(
    val latMin: Float,
    val latMax: Float,
    val lonMin: Float,
    val lonMax: Float,
    val nLat: Int,
    val nLon: Int,
    val validTimeSeconds: Long,
    val points: List<GridPoint>,
) {
    fun pointAt(row: Int, col: Int): GridPoint = points[row * nLon + col]

    /** 2D pressure grid, row-major — the shape Isobars/PressureCenters expect. */
    fun pressureGrid(): Array<DoubleArray> =
        Array(nLat) { row -> DoubleArray(nLon) { col -> pointAt(row, col).pressureHpa.toDouble() } }

    companion object {
        private val MAGIC = byteArrayOf('W'.code.toByte(), 'X'.code.toByte(), 'L'.code.toByte(), '1'.code.toByte())

        fun parse(bytes: ByteArray): WxlFile {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = ByteArray(4)
            buf.get(magic)
            require(magic.contentEquals(MAGIC)) {
                "Not a WXL1 file (bad magic: ${magic.joinToString { it.toString() }})"
            }

            val latMin = buf.float
            val latMax = buf.float
            val lonMin = buf.float
            val lonMax = buf.float
            // n_lat/n_lon are unsigned in the format; ByteBuffer.getShort()
            // is always signed, so mask to get the correct unsigned value.
            val nLat = buf.short.toInt() and 0xFFFF
            val nLon = buf.short.toInt() and 0xFFFF
            // Same for valid_time (unsigned 32-bit).
            val validTime = buf.int.toLong() and 0xFFFFFFFFL

            val points = ArrayList<GridPoint>(nLat * nLon)
            repeat(nLat * nLon) {
                val pressureRaw = buf.short.toInt() // signed 16-bit, no masking needed
                val tempC = buf.get().toInt() // signed byte, sign-extends correctly
                val windU = buf.get().toInt()
                val windV = buf.get().toInt()
                points.add(GridPoint(pressureRaw / 10.0f, tempC, windU, windV))
            }

            return WxlFile(latMin, latMax, lonMin, lonMax, nLat, nLon, validTime, points)
        }
    }
}
