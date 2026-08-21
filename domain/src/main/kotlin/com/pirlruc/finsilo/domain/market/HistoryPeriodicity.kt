package com.pirlruc.finsilo.domain.market

/**
 * Thins a time series by increasing step (every 2nd, 3rd, … point) instead of
 * dropping a date/count window. Stored daily bars are never deleted this way.
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
