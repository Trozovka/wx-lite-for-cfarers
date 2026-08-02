package com.trozovka.wxlite.map

import kotlin.math.cos
import kotlin.math.sin

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
}
