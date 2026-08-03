package com.trozovka.wxlite.data

/**
 * Per spec Section 1: "Free version (public) is: only 1-day forecast" /
 * "Paid version (private) is: 10-day forecast". MainActivity (also in
 * this module) selects between the two via an intent extra
 * (EXTRA_TIER); the free app's manifest launches it with no extras and
 * gets FREE by default, while wx-pro-for-cfarers's LicenseGateActivity
 * launches it explicitly with PAID after a successful license check
 * (or FREE on an invalid/missing license, the freemium fallback).
 */
enum class ForecastTier(val maxHour: Int) {
    FREE(24),
    PAID(240),
}

fun List<Int>.filterByTier(tier: ForecastTier): List<Int> = filter { it <= tier.maxHour }
