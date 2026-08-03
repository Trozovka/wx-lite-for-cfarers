package com.trozovka.wxlite.data

import android.content.Context

data class AreaPoint(val lat: Double, val lon: Double)

/**
 * Up to 10 waypoints defining the passage-plan area of interest, in
 * order -- replaces the old single "ship location" concept per explicit
 * operator direction (a fixed default position wasn't useful; captains
 * need to bound the actual area of their passage plan). A null entry
 * means that row was left blank; the chart connects only the filled-in
 * points, in the same order, to outline the area.
 */
class AreaStore(context: Context) {
    private val prefs = context.getSharedPreferences("passage_area", Context.MODE_PRIVATE)

    fun save(points: List<AreaPoint?>) {
        val editor = prefs.edit()
        for (i in 0 until MAX_POINTS) {
            val point = points.getOrNull(i)
            if (point != null) {
                editor.putFloat(latKey(i), point.lat.toFloat())
                editor.putFloat(lonKey(i), point.lon.toFloat())
            } else {
                editor.remove(latKey(i))
                editor.remove(lonKey(i))
            }
        }
        editor.apply()
    }

    /** Always returns exactly [MAX_POINTS] entries, in point order; unset
     * rows come back as null rather than being omitted, so callers can
     * always index by point number (1-10). */
    fun get(): List<AreaPoint?> = (0 until MAX_POINTS).map { i ->
        if (prefs.contains(latKey(i)) && prefs.contains(lonKey(i))) {
            AreaPoint(prefs.getFloat(latKey(i), 0f).toDouble(), prefs.getFloat(lonKey(i), 0f).toDouble())
        } else {
            null
        }
    }

    private fun latKey(i: Int) = "lat_$i"
    private fun lonKey(i: Int) = "lon_$i"

    companion object {
        const val MAX_POINTS = 10
    }
}
