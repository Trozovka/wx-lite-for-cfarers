package com.trozovka.wxlite.chart

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindBarbTest {

    private val knotsToMs = 1.0 / 1.943844

    @Test
    fun `zero wind is calm`() {
        val barb = WindBarb.fromComponents(0.0, 0.0)
        assertTrue(barb.isCalm)
        assertEquals(0, barb.speedKnots)
    }

    @Test
    fun `just under 3kt still rounds down to calm`() {
        // 2kt is right at the boundary — should round to 0, not 5.
        val barb = WindBarb.fromComponents(0.0, -2.0 * knotsToMs)
        assertTrue(barb.isCalm)
    }

    @Test
    fun `10kt from due north blowing south`() {
        // Wind FROM north means it's blowing southward: u=0 (no east-west),
        // v negative (southward).
        val barb = WindBarb.fromComponents(0.0, -10.0 * knotsToMs)
        assertEquals(10, barb.speedKnots)
        assertEquals(0.0, barb.fromDirectionDeg, 0.5)
        assertEquals(0, barb.pennants)
        assertEquals(1, barb.fullBarbs)
        assertEquals(0, barb.halfBarbs)
        assertTrue(!barb.isCalm)
    }

    @Test
    fun `45kt from due west blowing east decomposes into 4 full barbs and a half`() {
        // Wind FROM west means it's blowing eastward: u positive, v=0.
        val barb = WindBarb.fromComponents(45.0 * knotsToMs, 0.0)
        assertEquals(45, barb.speedKnots)
        assertEquals(270.0, barb.fromDirectionDeg, 0.5)
        assertEquals(0, barb.pennants)
        assertEquals(4, barb.fullBarbs)
        assertEquals(1, barb.halfBarbs)
    }

    @Test
    fun `65kt from due south blowing north uses a pennant plus a full barb plus a half`() {
        // Wind FROM south means it's blowing northward: u=0, v positive.
        val barb = WindBarb.fromComponents(0.0, 65.0 * knotsToMs)
        assertEquals(65, barb.speedKnots)
        assertEquals(180.0, barb.fromDirectionDeg, 0.5)
        assertEquals(1, barb.pennants)
        assertEquals(1, barb.fullBarbs)
        assertEquals(1, barb.halfBarbs)
    }

    @Test
    fun `direction round-trips correctly for a diagonal wind`() {
        // Construct u,v for a wind blowing TOWARD 225 degrees (southwest) —
        // i.e. FROM the northeast, 45 degrees — and confirm fromComponents
        // recovers that same from-direction, independent of any hand
        // arithmetic for the diagonal case.
        val towardBearingRad = Math.toRadians(225.0)
        val speedKnots = 20.0
        val u = speedKnots * knotsToMs * sin(towardBearingRad)
        val v = speedKnots * knotsToMs * cos(towardBearingRad)

        val barb = WindBarb.fromComponents(u, v)
        assertEquals(20, barb.speedKnots)
        assertEquals(45.0, barb.fromDirectionDeg, 0.5)
    }

    @Test
    fun `trueSpeedKnots preserves the unrounded speed, unlike the 5kt-rounded barb speedKnots`() {
        // 16.6kt rounds to 15kt for barb-drawing purposes (nearest 5),
        // but Beaufort classification must see the real 16.6 -- confirmed
        // during an accuracy audit that using the rounded value instead
        // shifts a real wind speed into the wrong Beaufort force (16.6kt
        // is force 5, but reading back from a 15kt rounding gives force 4).
        val barb = WindBarb.fromComponents(0.0, -16.6 * knotsToMs)
        assertEquals(15, barb.speedKnots)
        assertEquals(16.6, barb.trueSpeedKnots, 0.05)
        assertEquals(5, Beaufort.forceForKnots(barb.trueSpeedKnots))
        // The bug this guards against: force 4 is what you'd wrongly get
        // from the rounded speed instead.
        assertEquals(4, Beaufort.forceForKnots(barb.speedKnots.toDouble()))
    }
}
