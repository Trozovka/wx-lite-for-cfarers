package com.trozovka.wxlite.data

import android.content.Context

/**
 * The ship's saved position — persisted locally so it survives app
 * restart with zero connectivity, per spec ("Ship location must remain
 * saved locally").
 */
class LocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("ship_location", Context.MODE_PRIVATE)

    fun save(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun get(): ShipPosition? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        return ShipPosition(
            lat = prefs.getFloat(KEY_LAT, 0f).toDouble(),
            lon = prefs.getFloat(KEY_LON, 0f).toDouble(),
            savedAtMillis = prefs.getLong(KEY_SAVED_AT, 0L),
        )
    }

    companion object {
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_SAVED_AT = "saved_at"
    }
}

data class ShipPosition(val lat: Double, val lon: Double, val savedAtMillis: Long)
