package com.trozovka.wxlite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastTierTest {

    private val allScheduleHours = listOf(0, 6, 12, 18, 24, 30, 36, 42, 48, 54, 60, 66, 72, 84, 96, 240)

    @Test
    fun `FREE tier keeps only hours within 1 day`() {
        val filtered = allScheduleHours.filterByTier(ForecastTier.FREE)
        assertEquals(listOf(0, 6, 12, 18, 24), filtered)
    }

    @Test
    fun `PAID tier keeps everything up to 10 days`() {
        val filtered = allScheduleHours.filterByTier(ForecastTier.PAID)
        assertEquals(allScheduleHours, filtered)
    }
}
