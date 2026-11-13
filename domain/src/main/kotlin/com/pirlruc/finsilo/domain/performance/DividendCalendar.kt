package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.time.LocalDate

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
        val gaps = dates.zipWithNext { left, right -> monthGap(left, right) }
        return DividendCadence.entries.firstOrNull { cadence -> gaps.all { gap -> gap == months(cadence) } }
    }

    private fun monthGap(left: LocalDate, right: LocalDate): Int = (right.year - left.year) * 12 + (right.monthValue - left.monthValue)

    private fun months(cadence: DividendCadence): Int = when (cadence) {
        DividendCadence.MONTHLY -> 1
        DividendCadence.QUARTERLY -> 3
        DividendCadence.SEMIANNUAL -> 6
        DividendCadence.ANNUAL -> 12
    }

    private fun nextDate(last: LocalDate, cadence: DividendCadence, from: LocalDate): LocalDate? {
        var cursor = advance(last, cadence)
        var steps = 0
        while (!cursor.isAfter(from) && steps < MAX_STEPS) {
            cursor = advance(cursor, cadence)
            steps += 1
        }
        return cursor.takeIf { steps < MAX_STEPS && !cursor.isAfter(from.plusYears(1)) }
    }

    private fun advance(date: LocalDate, cadence: DividendCadence): LocalDate = when (cadence) {
        DividendCadence.MONTHLY -> date.plusMonths(1)
        DividendCadence.QUARTERLY -> date.plusMonths(3)
        DividendCadence.SEMIANNUAL -> date.plusMonths(6)
        DividendCadence.ANNUAL -> date.plusYears(1)
    }

    private fun forward(next: LocalDate, cadence: DividendCadence, from: LocalDate, amount: BigDecimal): BigDecimal {
        val until = from.plusYears(1)
        var cursor = next
        var count = 0
        while (!cursor.isAfter(until) && count < cadence.paymentsPerYear) {
            count += 1
            cursor = advance(cursor, cadence)
        }
        return amount.multiply(BigDecimal(count))
    }

    private const val MAX_STEPS = 4
}
