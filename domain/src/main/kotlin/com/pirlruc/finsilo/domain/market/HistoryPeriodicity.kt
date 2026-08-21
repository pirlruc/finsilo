package com.pirlruc.finsilo.domain.market

/**
 * Optional step increase for a chart working set. Not used on stored quotes or
 * the NAV chart: one holding is about 0.06–0.1 MB per year of daily closes
 * (TEXT dates and BigDecimal prices), so a 20-name book held 10 years is tens
 * of MB. SMA-200 and NAV rebuild need consecutive dailies, and a decade of
 * NAV points is a few thousand samples — not a reason to drop or step bars.
 */
object HistoryPeriodicity {
    fun <T> thin(values: List<T>, maxPoints: Int, keepLast: T?): List<T> {
        val cap = maxPoints.coerceAtLeast(1)
        if (values.size <= cap) return values
        val step = (values.size + cap - 1) / cap
        val points = values.filterIndexed { index, _ -> index % step == 0 }.toMutableList()
        if (keepLast != null && points.last() != keepLast) points += keepLast
        return points
    }
}
