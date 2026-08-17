package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.NavInputsFingerprint
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

data class NavRebuildDecision(val skip: Boolean, val fingerprint: String, val points: List<NavPoint>)

/**
 * Dense daily NAV series for persistence. Skip when [NavInputsFingerprint] matches
 * and stored points already cover first transaction through [asOf]. If only the
 * end date moved forward, append those days instead of walking the whole series.
 */
class RebuildNavHistoryUseCase {
    operator fun invoke(
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        storedFingerprint: String?,
        storedPoints: List<NavPoint>,
    ): NavRebuildDecision {
        val fingerprint = NavInputsFingerprint.of(snapshot)
        val first = snapshot.transactions.minOfOrNull { it.date }
        if (first == null) {
            return NavRebuildDecision(skip = false, fingerprint = fingerprint, points = emptyList())
        }
        if (covers(storedFingerprint, fingerprint, storedPoints, first, asOf)) {
            return NavRebuildDecision(skip = true, fingerprint = fingerprint, points = storedPoints)
        }
        val valuator = PortfolioValuator()
        val points = appendOrRebuild(valuator, snapshot, first, asOf, storedFingerprint == fingerprint, storedPoints)
        return NavRebuildDecision(skip = false, fingerprint = fingerprint, points = points)
    }

    private fun covers(
        storedFingerprint: String?,
        fingerprint: String,
        storedPoints: List<NavPoint>,
        first: LocalDate,
        asOf: LocalDate,
    ): Boolean {
        if (storedFingerprint != fingerprint || storedPoints.isEmpty()) return false
        val storedFirst = storedPoints.minOf { it.date }
        val storedLast = storedPoints.maxOf { it.date }
        return !storedFirst.isAfter(first) && !storedLast.isBefore(asOf)
    }

    private fun appendOrRebuild(
        valuator: PortfolioValuator,
        snapshot: PortfolioSnapshot,
        first: LocalDate,
        asOf: LocalDate,
        fingerprintMatches: Boolean,
        storedPoints: List<NavPoint>,
    ): List<NavPoint> {
        if (fingerprintMatches && storedPoints.isNotEmpty()) {
            val storedFirst = storedPoints.minOf { it.date }
            val storedLast = storedPoints.maxOf { it.date }
            if (!storedFirst.isAfter(first) && storedLast.isBefore(asOf)) {
                return storedPoints + walk(valuator, snapshot, storedLast.plusDays(1), asOf)
            }
        }
        return walk(valuator, snapshot, first, asOf)
    }

    private fun walk(valuator: PortfolioValuator, snapshot: PortfolioSnapshot, from: LocalDate, to: LocalDate): List<NavPoint> {
        val points = ArrayList<NavPoint>()
        var date = from
        while (!date.isAfter(to)) {
            points += NavPoint(date = date, valueEur = valuator.totalNavEur(snapshot, date))
            date = date.plusDays(1)
        }
        return points
    }
}
