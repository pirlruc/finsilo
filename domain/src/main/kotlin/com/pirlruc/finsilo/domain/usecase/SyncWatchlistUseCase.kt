package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import java.time.LocalDate

/** Quotes pulled for a watchlist. Failures stay local; the live ledger is untouched. */
data class WatchlistSyncResult(val quotes: List<DailyMarketData>, val failures: List<String>)

/**
 * GET-only refresh of watchlist symbols. Does not write [com.pirlruc.finsilo.domain.model.Transaction]
 * rows and does not request Alpha Vantage OVERVIEW (quota stays on the live book).
 */
class SyncWatchlistUseCase(private val feed: MarketFeed) {
    suspend operator fun invoke(watchlist: WatchlistSnapshot, asOf: LocalDate = LocalDate.now()): WatchlistSyncResult {
        val failures = ArrayList<String>()
        val rows = ArrayList<DailyMarketData>()
        for (item in watchlist.items) {
            val history = loadHistory(item, asOf, failures)
            if (history.isNotEmpty()) rows += barsFor(item, history)
        }
        return WatchlistSyncResult(quotes = rows, failures = failures)
    }

    private suspend fun loadHistory(item: WatchlistItem, asOf: LocalDate, failures: MutableList<String>): List<PriceBar> =
        runCatching { feed.dailyHistory(item.asFeedAsset(), asOf) }
            .onFailure { failures += "${item.symbol}: ${it.message}" }
            .getOrNull()
            .orEmpty()
            .filter { !it.date.isAfter(asOf) }

    private fun barsFor(item: WatchlistItem, history: List<PriceBar>): List<DailyMarketData> {
        val from = (history.size - MAX_BARS).coerceAtLeast(0)
        return (from until history.size).map { index ->
            val bar = history[index]
            val closes = history.subList(0, index + 1).map { it.closeNative }
            DailyMarketData(
                assetId = item.id,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = AnalystRating.NONE,
                sma50 = MovingAverages.sma(closes, 50),
                sma200 = MovingAverages.sma(closes, 200),
            )
        }
    }

    companion object {
        private const val MAX_BARS: Int = 400
    }
}
