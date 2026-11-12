package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Regular dividend spacing inferred from historical pay dates. */
enum class DividendCadence(val days: Int, val paymentsPerYear: Int) {
    MONTHLY(30, 12),
    QUARTERLY(91, 4),
    SEMIANNUAL(182, 2),
    ANNUAL(365, 1),
}

/** Next pay date and income over the following 12 months. Null fields mean no projection. */
data class DividendProjection(val cadence: DividendCadence, val nextDate: LocalDate, val forwardIncomeEur: BigDecimal)

/**
 * Infers a dividend cadence from pay dates. Irregular gaps produce no guessed date.
 */
object DividendCalendar {
    fun project(dates: List<LocalDate>, lastAmountEur: BigDecimal, from: LocalDate): DividendProjection? {
        val cadence = cadenceOf(dates.distinct().sorted()) ?: return null
        val next = nextDate(dates.max(), cadence, from) ?: return null
        return DividendProjection(cadence, next, forward(next, cadence, from, lastAmountEur))
    }

    private fun cadenceOf(dates: List<LocalDate>): DividendCadence? {
        if (dates.size < 3) return null
        val gaps = dates.zipWithNext { left, right -> ChronoUnit.DAYS.between(left, right) }
        return DividendCadence.entries.firstOrNull { cadence -> gaps.all { gap -> gap in band(cadence) } }
    }

    private fun band(cadence: DividendCadence): LongRange = when (cadence) {
        DividendCadence.MONTHLY -> 25L..35L
        DividendCadence.QUARTERLY -> 80L..100L
        DividendCadence.SEMIANNUAL -> 170L..200L
        DividendCadence.ANNUAL -> 350L..380L
    }

    private fun nextDate(last: LocalDate, cadence: DividendCadence, from: LocalDate): LocalDate? {
        var cursor = last.plusDays(cadence.days.toLong())
        var steps = 0
        while (!cursor.isAfter(from) && steps < MAX_STEPS) {
            cursor = cursor.plusDays(cadence.days.toLong())
            steps += 1
        }
        return cursor.takeIf { steps < MAX_STEPS && !cursor.isAfter(from.plusYears(1)) }
    }

    private fun forward(next: LocalDate, cadence: DividendCadence, from: LocalDate, amount: BigDecimal): BigDecimal {
        val until = from.plusYears(1)
        var cursor = next
        var count = 0
        while (!cursor.isAfter(until) && count < cadence.paymentsPerYear) {
            count += 1
            cursor = cursor.plusDays(cadence.days.toLong())
        }
        return amount.multiply(BigDecimal(count))
    }

    private const val MAX_STEPS = 4
}
