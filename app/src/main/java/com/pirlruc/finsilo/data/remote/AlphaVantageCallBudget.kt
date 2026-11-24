package com.pirlruc.finsilo.data.remote

import java.time.LocalDate

/** Free-tier Alpha Vantage cap shared by quotes and statement calls. */
class AlphaVantageCallBudget(
    private val limit: Int = DAILY_LIMIT,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private var day: LocalDate = today()
    private var used: Int = 0

    fun take(): Boolean {
        val now = today()
        if (now != day) {
            day = now
            used = 0
        }
        if (used >= limit) return false
        used += 1
        return true
    }

    companion object {
        const val DAILY_LIMIT: Int = 25
    }
}
