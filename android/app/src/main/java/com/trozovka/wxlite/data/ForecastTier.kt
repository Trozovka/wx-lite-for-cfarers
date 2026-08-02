package com.trozovka.wxlite.data

/**
 * Per spec Section 1: "Free version (public) is: only 1-day forecast" /
 * "Paid version (private) is: 10-day forecast". This repo (the public
 * core) is hardcoded to FREE — the private paid app depends on this same
 * code but is expected to select PAID once Section 9's exact cross-repo
 * dependency mechanism is decided. Deliberately not building that
 * mechanism yet rather than guessing at it.
 */
enum class ForecastTier(val maxHour: Int) {
    FREE(24),
    PAID(240),
}

fun List<Int>.filterByTier(tier: ForecastTier): List<Int> = filter { it <= tier.maxHour }
