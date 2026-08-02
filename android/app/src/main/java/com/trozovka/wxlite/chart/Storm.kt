package com.trozovka.wxlite.chart

/**
 * A currently active tropical cyclone. Parsing from NHC's JSON (which uses
 * org.json — an Android-platform class that's stubbed out and throws in
 * plain JVM unit tests, confirmed empirically) lives in the repository
 * layer; this is the plain data type plus the pure, testable filtering
 * logic that doesn't need Android at all.
 */
data class Storm(
    val id: String,
    val name: String,
    val classification: String,
    val pressureHpa: Int?,
    val lat: Double,
    val lon: Double,
)

object Storms {
    fun withinBounds(
        storms: List<Storm>,
        latMin: Double,
        latMax: Double,
        lonMin: Double,
        lonMax: Double,
    ): List<Storm> = storms.filter { it.lat in latMin..latMax && it.lon in lonMin..lonMax }
}
