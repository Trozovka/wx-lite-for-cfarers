package com.trozovka.wxlite.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowPressureTrackerTest {

    @Test
    fun `a low moving due north is tracked with bearing 0`() {
        val tracked = LowPressureTracker.track(
            fromLows = listOf(Pair(10.0, 120.0)),
            toLows = listOf(Pair(12.0, 120.0)),
        )
        assertEquals(1, tracked.size)
        assertEquals(10.0, tracked[0].lat, 0.001)
        assertEquals(120.0, tracked[0].lon, 0.001)
        assertEquals(0.0, tracked[0].bearingDeg, 0.5)
    }

    @Test
    fun `a low moving due east is tracked with bearing 90`() {
        val tracked = LowPressureTracker.track(
            fromLows = listOf(Pair(10.0, 120.0)),
            toLows = listOf(Pair(10.0, 123.0)),
        )
        assertEquals(90.0, tracked[0].bearingDeg, 0.5)
    }

    @Test
    fun `each low matches its own nearest counterpart, not a distant one`() {
        // Two lows far apart, each with a plausible match nearby -- must
        // not accidentally cross-match the distant pair.
        val tracked = LowPressureTracker.track(
            fromLows = listOf(Pair(10.0, 120.0), Pair(40.0, 10.0)),
            toLows = listOf(Pair(11.0, 121.0), Pair(41.0, 11.0)),
        )
        assertEquals(2, tracked.size)
        val nearManila = tracked.first { it.lat == 10.0 }
        assertTrue(nearManila.bearingDeg in 0.0..90.0) // moved north-east
    }

    @Test
    fun `a low with no plausible match within range is dropped, not force-matched`() {
        val tracked = LowPressureTracker.track(
            fromLows = listOf(Pair(10.0, 120.0)),
            toLows = listOf(Pair(50.0, 170.0)), // absurdly far -- not the same system
            maxDistanceDeg = 15.0,
        )
        assertEquals(0, tracked.size)
    }

    @Test
    fun `no destination lows at all means nothing is tracked, not a crash`() {
        val tracked = LowPressureTracker.track(fromLows = listOf(Pair(10.0, 120.0)), toLows = emptyList())
        assertEquals(0, tracked.size)
    }
}
