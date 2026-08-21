package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.market.QuoteSyncPlanner
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import java.time.LocalDate

/** Quotes pulled for a watchlist. Failures stay local; the live ledger is untouched. */
data class WatchlistSyncResult(val quotes: List<DailyMarketData>, val failures: List<String>)

/**
 * GET-only refresh of watchlist symbols. Oldest quotes first; skip names that
 * already have an as-of close. OVERVIEW runs only when rating alerts are on
 * (default Buy / Strong Buy) and the last rating is older than 7 days.
 */
class SyncWatchlistUseCase(private val feed: MarketFeed) {
    suspend operator fun invoke(
        watchlist: WatchlistSnapshot,
        asOf: LocalDate = LocalDate.now(),
        prefs: List<RatingAlertPref> = emptyList(),
    ): WatchlistSyncResult {
        val failures = ArrayList<String>()
        val rows = ArrayList<DailyMarketData>()
        val ordered =
            QuoteSyncPlanner.oldestFirst(watchlist.items) { item ->
                QuoteSyncPlanner.lastBarDate(watchlist.quotes, item.id)
            }
        for (item in ordered) {
            rows += syncItem(item, watchlist.quotes, asOf, prefs, failures)
        }
        return WatchlistSyncResult(quotes = rows, failures = failures)
    }

    private suspend fun syncItem(
        item: WatchlistItem,
        storedAll: List<DailyMarketData>,
        asOf: LocalDate,
        prefs: List<RatingAlertPref>,
        failures: MutableList<String>,
    ): List<DailyMarketData> {
        val stored = storedAll.filter { it.assetId == item.id }
        val last = QuoteSyncPlanner.lastBarDate(stored, item.id)
        val rating = ratingFor(item, stored, asOf, prefs, failures)
        if (QuoteSyncPlanner.isFresh(last, asOf)) {
            return patchFreshRating(stored, rating)
        }
        val history = loadHistory(item, asOf, failures)
        if (history.isEmpty()) return emptyList()
        return barsFor(item, history, stored, rating)
    }

    private fun patchFreshRating(stored: List<DailyMarketData>, rating: AnalystRating): List<DailyMarketData> {
        val latest = stored.maxByOrNull { it.date } ?: return emptyList()
        if (latest.analystRating == rating) return emptyList()
        return listOf(latest.copy(analystRating = rating))
    }

    private suspend fun loadHistory(item: WatchlistItem, asOf: LocalDate, failures: MutableList<String>): List<PriceBar> =
        runCatching { feed.dailyHistory(item.asFeedAsset(), asOf) }
            .onFailure { failures += "${item.symbol}: ${it.message}" }
            .getOrNull()
            .orEmpty()
            .filter { !it.date.isAfter(asOf) }

    private fun barsFor(
        item: WatchlistItem,
        history: List<PriceBar>,
        stored: List<DailyMarketData>,
        rating: AnalystRating,
    ): List<DailyMarketData> {
        val storedByDate = stored.associateBy { it.date }
        return history.indices.map { index ->
            val bar = history[index]
            val closes = history.subList(0, index + 1).map { it.closeNative }
            val isLatest = index == history.lastIndex
            DailyMarketData(
                assetId = item.id,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = ratingOnBar(isLatest, rating, storedByDate[bar.date]),
                sma50 = MovingAverages.sma(closes, 50),
                sma200 = MovingAverages.sma(closes, 200),
            )
        }
    }

    private fun ratingOnBar(isLatest: Boolean, latest: AnalystRating, stored: DailyMarketData?): AnalystRating {
        if (isLatest) return latest
        val previous = stored?.analystRating
        return if (previous != null && previous != AnalystRating.NONE) previous else AnalystRating.NONE
    }

    private suspend fun ratingFor(
        item: WatchlistItem,
        stored: List<DailyMarketData>,
        asOf: LocalDate,
        prefs: List<RatingAlertPref>,
        failures: MutableList<String>,
    ): AnalystRating {
        val pref = prefs.firstOrNull { it.scope == RatingAlertScope.WATCHLIST && it.targetId == item.id }
        if (RatingAlertPref.effective(pref, RatingAlertScope.WATCHLIST).isEmpty()) return AnalystRating.NONE
        val fresh =
            stored
                .filter { it.analystRating != AnalystRating.NONE && !it.date.isBefore(asOf.minusDays(OVERVIEW_MAX_AGE_DAYS)) }
                .maxByOrNull { it.date }
        if (fresh != null) return fresh.analystRating
        return runCatching { feed.analystRating(item.asFeedAsset()) }
            .onFailure { failures += "${item.symbol} rating: ${it.message}" }
            .getOrDefault(AnalystRating.NONE)
    }

    companion object {
        private const val OVERVIEW_MAX_AGE_DAYS: Long = 7
    }
}
