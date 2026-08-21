package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import java.time.LocalDate

/**
 * Edge-triggered rating notifications from stored bars only.
 * Fires when the latest rating enters the selected set.
 */
class GetRatingAlertsUseCase {
    operator fun invoke(
        signals: List<MarketSignal>,
        watchlistItems: List<WatchlistItem>,
        watchlistQuotes: List<DailyMarketData>,
        prefs: List<RatingAlertPref>,
        asOf: LocalDate,
    ): List<PortfolioAlert> {
        val byTarget = prefs.associateBy { it.scope to it.targetId }
        val holdingAlerts = signals.mapNotNull { signal ->
            alertIfEntered(
                symbol = signal.asset.symbol,
                previous = signal.previousRating,
                current = signal.rating,
                selected = RatingAlertPref.effective(
                    byTarget[RatingAlertScope.HOLDING to signal.asset.id],
                    RatingAlertScope.HOLDING,
                ),
            )
        }
        return holdingAlerts + watchlistAlerts(watchlistItems, watchlistQuotes, byTarget, asOf)
    }

    private fun watchlistAlerts(
        items: List<WatchlistItem>,
        quotes: List<DailyMarketData>,
        byTarget: Map<Pair<RatingAlertScope, String>, RatingAlertPref>,
        asOf: LocalDate,
    ): List<PortfolioAlert> {
        val byItem = quotes.filter { !it.date.isAfter(asOf) }.groupBy { it.assetId }
        return items.mapNotNull { item ->
            val series = byItem[item.id].orEmpty().sortedBy { it.date }
            val today = series.lastOrNull() ?: return@mapNotNull null
            val yesterday = series.getOrNull(series.lastIndex - 1)
            alertIfEntered(
                symbol = item.symbol,
                previous = yesterday?.analystRating,
                current = today.analystRating,
                selected = RatingAlertPref.effective(
                    byTarget[RatingAlertScope.WATCHLIST to item.id],
                    RatingAlertScope.WATCHLIST,
                ),
            )
        }
    }

    private fun alertIfEntered(
        symbol: String,
        previous: AnalystRating?,
        current: AnalystRating,
        selected: Set<AnalystRating>,
    ): PortfolioAlert? {
        if (current == AnalystRating.NONE || current !in selected) return null
        val prior = previous.takeUnless { it == AnalystRating.NONE }
        if (prior != null && prior in selected) return null
        if (prior == null && previous == AnalystRating.NONE) return null
        val from = prior?.displayName ?: "None"
        return PortfolioAlert(
            AlertChannel.RATING,
            "$symbol rating",
            "$from → ${current.displayName}",
        )
    }
}
