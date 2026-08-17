package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.HistoryReport
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class GetAllocationUseCase(private val valuator: PortfolioValuator = PortfolioValuator()) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): AllocationReport = valuator.allocation(snapshot, asOf)
}

class GetPortfolioHistoryUseCase(private val valuator: PortfolioValuator = PortfolioValuator(), private val maxPoints: Int = 180) {
    operator fun invoke(snapshot: PortfolioSnapshot, range: HistoryRange, asOf: LocalDate): HistoryReport {
        val firstTx = snapshot.transactions.minOfOrNull { it.date }
        if (firstTx == null) {
            return HistoryReport(range = range, from = asOf, to = asOf, points = emptyList())
        }
        val from = rangeStart(range, asOf, firstTx)
        val to = asOf
        if (from.isAfter(to)) {
            return HistoryReport(range = range, from = from, to = to, points = emptyList())
        }

        val dayCount = ChronoUnit.DAYS.between(from, to).toInt() + 1
        val step = if (dayCount <= maxPoints) 1 else ((dayCount + maxPoints - 1) / maxPoints)
        val points = ArrayList<NavPoint>()
        var date = from
        while (!date.isAfter(to)) {
            points += NavPoint(date = date, valueEur = valuator.totalNavEur(snapshot, date))
            date = date.plusDays(step.toLong())
        }
        if (points.lastOrNull()?.date != to) {
            points += NavPoint(date = to, valueEur = valuator.totalNavEur(snapshot, to))
        }
        return HistoryReport(range = range, from = from, to = to, points = points)
    }

    private fun rangeStart(range: HistoryRange, asOf: LocalDate, firstTx: LocalDate): LocalDate {
        val candidate =
            when (range) {
                HistoryRange.ONE_MONTH -> asOf.minusMonths(1)
                HistoryRange.THREE_MONTHS -> asOf.minusMonths(3)
                HistoryRange.YTD -> LocalDate.of(asOf.year, 1, 1)
                HistoryRange.ALL -> firstTx
            }
        return maxOf(candidate, firstTx)
    }
}
