package com.pirlruc.finsilo.domain.model

import java.time.LocalDate

/** Inclusive start of a dashboard NAV window, not before the first ledger date. */
fun HistoryRange.startDate(asOf: LocalDate, firstTx: LocalDate): LocalDate {
    val candidate =
        when (this) {
            HistoryRange.ONE_MONTH -> asOf.minusMonths(1)
            HistoryRange.THREE_MONTHS -> asOf.minusMonths(3)
            HistoryRange.SIX_MONTHS -> asOf.minusMonths(6)
            HistoryRange.ONE_YEAR -> asOf.minusYears(1)
            HistoryRange.YTD -> LocalDate.of(asOf.year, 1, 1)
            HistoryRange.ALL -> firstTx
        }
    return maxOf(candidate, firstTx)
}
