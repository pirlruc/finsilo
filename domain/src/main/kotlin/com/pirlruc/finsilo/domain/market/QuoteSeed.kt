package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PriceBar
import java.time.LocalDate

/** Turns a probe or sync history into stored daily rows, including locally computed SMAs. */
object QuoteSeed {
    const val OVERVIEW_MAX_AGE_DAYS: Long = 7

    fun fromBars(
        assetId: String,
        bars: List<PriceBar>,
        from: LocalDate? = null,
        rating: AnalystRating = AnalystRating.NONE,
        storedByDate: Map<LocalDate, DailyMarketData> = emptyMap(),
    ): List<DailyMarketData> {
        val relevant = if (from == null) bars else bars.filter { !it.date.isBefore(from) }
        if (relevant.isEmpty()) return emptyList()
        val last = relevant.lastIndex
        return relevant.indices.map { index ->
            val bar = relevant[index]
            val closes = relevant.subList(0, index + 1).map { it.closeNative }
            DailyMarketData(
                assetId = assetId,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = QuoteRating.onBar(index == last, rating, storedByDate[bar.date]),
                sma50 = MovingAverages.sma(closes, 50),
                sma200 = MovingAverages.sma(closes, 200),
            )
        }
    }

    fun storedOverview(stored: List<DailyMarketData>, asOf: LocalDate): AnalystRating? = stored
        .filter { it.analystRating != AnalystRating.NONE && !it.date.isBefore(asOf.minusDays(OVERVIEW_MAX_AGE_DAYS)) }
        .maxByOrNull { it.date }
        ?.analystRating

    /** Copy the latest stored bar only when its rating changed. */
    fun patchFreshRating(stored: List<DailyMarketData>, rating: AnalystRating): List<DailyMarketData> {
        val latest = stored.maxByOrNull { it.date } ?: return emptyList()
        if (latest.analystRating == rating) return emptyList()
        return listOf(latest.copy(analystRating = rating))
    }
}
