package com.trozovka.wxlite.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Degrees-minutes lat/lon, the maritime charting convention (not decimal
 * degrees) -- "14-36.0 N", "121-00.0 E". Pure conversions with no Android
 * dependency, so round-trip accuracy is directly unit-testable rather than
 * trusted on inspection.
 */
object Coordinates {
    data class DegMin(val degrees: Int, val minutes: Double, val positive: Boolean)

    fun decimalToDegMin(decimal: Double): DegMin {
        val positive = decimal >= 0
        val magnitude = abs(decimal)
        var degrees = floor(magnitude).toInt()
        var minutes = (magnitude - degrees) * 60.0
        // 1-decimal-place display means anything from 59.95 up rounds to
        // "60.0", which isn't a valid minutes value -- roll it into the
        // next whole degree instead of ever displaying "60.0".
        if (minutes >= 59.95) {
            degrees += 1
            minutes = 0.0
        }
        return DegMin(degrees, minutes, positive)
    }

    fun degMinToDecimal(degrees: Int, minutes: Double, positive: Boolean): Double {
        val magnitude = degrees + minutes / 60.0
        return if (positive) magnitude else -magnitude
    }

    fun isValidLat(degrees: Int, minutes: Double): Boolean =
        degrees in 0..90 && minutes in 0.0..59.9 && !(degrees == 90 && minutes > 0.0)

    fun isValidLon(degrees: Int, minutes: Double): Boolean =
        degrees in 0..180 && minutes in 0.0..59.9 && !(degrees == 180 && minutes > 0.0)

    fun formatLat(decimalLat: Double): String {
        val dm = decimalToDegMin(decimalLat)
        val hemisphere = if (dm.positive) "N" else "S"
        return "%02d-%04.1f %s".format(dm.degrees, dm.minutes, hemisphere)
    }

    fun formatLon(decimalLon: Double): String {
        val dm = decimalToDegMin(decimalLon)
        val hemisphere = if (dm.positive) "E" else "W"
        return "%03d-%04.1f %s".format(dm.degrees, dm.minutes, hemisphere)
    }

    /** Smallest whole degree >= the given value -- used by the map grid to
     * find where to start drawing lines across a visible range. */
    fun ceilDegree(value: Double): Int = ceil(value).toInt()

    /** Largest whole degree <= the given value. */
    fun floorDegree(value: Double): Int = floor(value).toInt()
}
