package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.HistoryReport
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

/** Current allocation by investment type. */
class GetAllocationUseCase(private val valuator: PortfolioValuator = PortfolioValuator()) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): AllocationReport = valuator.allocation(snapshot, asOf)
}

/** Dense daily NAV walk, optionally fed from persisted [storedNav], then downsampled. */
class GetPortfolioHistoryUseCase(private val valuator: PortfolioValuator = PortfolioValuator(), private val maxPoints: Int = 180) {
    operator fun invoke(
        snapshot: PortfolioSnapshot,
        range: HistoryRange,
        asOf: LocalDate,
        storedNav: List<NavPoint> = emptyList(),
    ): HistoryReport {
        val firstTx = snapshot.transactions.minOfOrNull { it.date }
        if (firstTx == null) {
            return HistoryReport(range = range, from = asOf, to = asOf, points = emptyList())
        }
        val from = rangeStart(range, asOf, firstTx)
        val to = asOf
        if (from.isAfter(to)) {
            return HistoryReport(range = range, from = from, to = to, points = emptyList())
        }
        val dense =
            if (covers(storedNav, snapshot, from, to)) {
                storedNav.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            } else {
                walk(snapshot, from, to)
            }
        return HistoryReport(range = range, from = from, to = to, points = downsample(dense, to))
    }

    private fun covers(stored: List<NavPoint>, snapshot: PortfolioSnapshot, from: LocalDate, to: LocalDate): Boolean {
        if (stored.isEmpty()) return false
        val first = stored.minOf { it.date }
        val last = stored.maxOf { it.date }
        val atFrom = stored.find { it.date == from }
        val atTo = stored.find { it.date == to }
        return atFrom != null &&
            atTo != null &&
            !first.isAfter(from) &&
            !last.isBefore(to) &&
            atFrom.valueEur.compareTo(valuator.totalNavEur(snapshot, from)) == 0 &&
            atTo.valueEur.compareTo(valuator.totalNavEur(snapshot, to)) == 0
    }

    private fun walk(snapshot: PortfolioSnapshot, from: LocalDate, to: LocalDate): List<NavPoint> {
        val points = ArrayList<NavPoint>()
        var date = from
        while (!date.isAfter(to)) {
            points += NavPoint(date = date, valueEur = valuator.totalNavEur(snapshot, date))
            date = date.plusDays(1)
        }
        return points
    }

    private fun downsample(dense: List<NavPoint>, to: LocalDate): List<NavPoint> {
        if (dense.size <= maxPoints) return dense
        val step = (dense.size + maxPoints - 1) / maxPoints
        val points = dense.filterIndexed { index, _ -> index % step == 0 }.toMutableList()
        if (points.lastOrNull()?.date != to) {
            dense.lastOrNull()?.let { points += it }
        }
        return points
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
