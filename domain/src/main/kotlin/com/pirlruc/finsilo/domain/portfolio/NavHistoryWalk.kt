package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.time.LocalDate

/** Dense calendar-day NAV series used by charts and rebuild. */
object NavHistoryWalk {
    fun points(
        valuator: PortfolioValuator,
        snapshot: PortfolioSnapshot,
        from: LocalDate,
        to: LocalDate,
    ): List<NavPoint> {
        val points = ArrayList<NavPoint>()
        var date = from
        while (!date.isAfter(to)) {
            points += NavPoint(date = date, valueEur = valuator.totalNavEur(snapshot, date))
            date = date.plusDays(1)
        }
        return points
    }
}
