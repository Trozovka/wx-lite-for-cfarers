package com.trozovka.wxlite.map

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ProjectedPoint(val x: Double, val y: Double)

/**
 * Orthographic projection — a sphere viewed from infinitely far away, the
 * classic "globe" look (what Ventusky's zoomed-out view uses conceptually).
 * No 3D engine, no rotation gestures beyond changing centerLonDeg: just
 * projection math onto a 2D circle, which is enough for a low-detail
 * offline overview and stays firmly in "lightweight custom rendering"
 * territory rather than pulling in a real 3D/mapping library.
 *
 * Screen convention: y grows downward (standard for Canvas), x/y are
 * relative to the globe's own center — caller adds the actual screen
 * center offset.
 */
object GlobeProjection {
    // The poles sit exactly on the visibility boundary for any
    // equator-centered view (always 90 degrees from the view center),
    // where cos(lat) should be exactly 0 but floating point rounds it to
    // a tiny value with essentially arbitrary sign — an epsilon tolerance
    // keeps that boundary case visible (rendered at the globe's edge)
    // instead of flickering in/out based on longitude. Caught by a real
    // test failure, not applied speculatively.
    private const val VISIBILITY_EPSILON = 1e-9

    /** Null if the point is on the far side of the globe (not visible). */
    fun project(latDeg: Double, lonDeg: Double, centerLonDeg: Double, radius: Double): ProjectedPoint? {
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg - centerLonDeg)

        val visibility = cos(lat) * cos(lon)
        if (visibility < -VISIBILITY_EPSILON) return null

        val x = radius * cos(lat) * sin(lon)
        val y = -radius * sin(lat) // negated: screen Y grows downward, north should render up
        return ProjectedPoint(x, y)
    }

    /**
     * Inverse of [project], for an equatorial-centered orthographic view
     * (lat0 = 0 — matches project's lack of a centerLat parameter).
     * Standard inverse-orthographic formulas (Snyder), adapted for our
     * negated screen Y. Null if the tap fell outside the globe's disc.
     */
    fun unproject(xScreen: Double, yScreen: Double, centerLonDeg: Double, radius: Double): LatLon? {
        val rho = sqrt(xScreen * xScreen + yScreen * yScreen)
        if (rho > radius) return null
        if (rho < 1e-9) return LatLon(0.0, centerLonDeg)

        val yStandard = -yScreen
        val c = asin((rho / radius).coerceIn(-1.0, 1.0))
        val lat = asin((yStandard * sin(c) / rho).coerceIn(-1.0, 1.0))
        val lon = centerLonDeg + Math.toDegrees(atan2(xScreen * sin(c), rho * cos(c)))
        return LatLon(Math.toDegrees(lat), lon)
    }
}
