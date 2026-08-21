package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.HistoryPeriodicity
import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.HistoryReport
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.startDate
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
        val from = range.startDate(asOf, firstTx)
        val to = asOf
        if (from.isAfter(to)) {
            return HistoryReport(range = range, from = from, to = to, points = emptyList())
        }
        val dense =
            if (covers(storedNav, snapshot, from, to)) {
                storedNav.filter { point -> !point.date.isBefore(from) && !point.date.isAfter(to) }
            } else {
                walk(snapshot, from, to)
            }
        return HistoryReport(range = range, from = from, to = to, points = HistoryPeriodicity.thin(dense, maxPoints, dense.lastOrNull()))
    }

    private fun covers(stored: List<NavPoint>, snapshot: PortfolioSnapshot, from: LocalDate, to: LocalDate): Boolean {
        if (!rangeCovered(stored, from, to)) return false
        val atFrom = stored.find { it.date == from } ?: return false
        val atTo = stored.find { it.date == to } ?: return false
        return matchesStored(snapshot, from, atFrom) && matchesStored(snapshot, to, atTo)
    }

    private fun matchesStored(snapshot: PortfolioSnapshot, date: LocalDate, point: NavPoint): Boolean {
        if (!canValue(snapshot, date)) return true
        return point.valueEur.compareTo(valuator.totalNavEur(snapshot, date)) == 0
    }

    private fun canValue(snapshot: PortfolioSnapshot, date: LocalDate): Boolean = snapshot.marketData.any { !it.date.isAfter(date) }

    private fun rangeCovered(stored: List<NavPoint>, from: LocalDate, to: LocalDate): Boolean {
        if (stored.isEmpty()) return false
        var first = stored[0].date
        var last = stored[0].date
        for (index in 1 until stored.size) {
            val date = stored[index].date
            if (date.isBefore(first)) first = date
            if (date.isAfter(last)) last = date
        }
        if (first.isAfter(from)) return false
        return !last.isBefore(to)
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
}
