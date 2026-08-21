package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.DailyMarketData
import java.time.LocalDate

/**
 * Sync order and skip-fresh rules so one Alpha Vantage key is spent on the
 * oldest quotes first and not on names that already have an as-of close.
 */
object QuoteSyncPlanner {
    /** True when a stored bar already covers [asOf], so daily history can be skipped. */
    fun isFresh(lastBar: LocalDate?, asOf: LocalDate): Boolean = lastBar != null && !lastBar.isBefore(asOf)

    /** Latest stored close for [assetId], or null when the book has no bars. */
    fun lastBarDate(rows: List<DailyMarketData>, assetId: String): LocalDate? =
        rows.filter { it.assetId == assetId }.maxOfOrNull { it.date }

    /** Never-quoted items first, then oldest last-bar date. */
    fun <T> oldestFirst(items: List<T>, lastBar: (T) -> LocalDate?): List<T> = items.sortedWith(compareBy { lastBar(it) ?: LocalDate.MIN })
}
