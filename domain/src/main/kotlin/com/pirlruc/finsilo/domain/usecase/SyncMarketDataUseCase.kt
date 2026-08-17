package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import java.time.LocalDate

/** Quotes and FX produced by one sync, plus per-symbol failures. */
data class MarketSyncResult(
    val marketData: List<DailyMarketData>,
    val fxRates: List<CurrencyRate> = emptyList(),
    val failures: List<String>,
)

/**
 * Pulls GET-only public quotes and writes daily rows. SMA 50/200 are computed
 * locally from stored closes so Alpha Vantage's free quota is not spent on SMA.
 */
class SyncMarketDataUseCase(private val feed: MarketFeed) {
    suspend operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate = LocalDate.now()): MarketSyncResult {
        val failures = ArrayList<String>()
        val rows = ArrayList<DailyMarketData>()
        snapshot.assets.filterNot { it.locallyValued || it.assetType == AssetType.CASH }.forEach { asset ->
            val history =
                runCatching { feed.dailyHistory(asset, asOf) }
                    .onFailure { failures += "${asset.symbol}: ${it.message}" }
                    .getOrNull()
            if (history != null) {
                rows += barsFor(asset, history, snapshot, asOf, failures)
            }
        }
        val fxFrom = snapshot.transactions.minOfOrNull { it.date } ?: asOf.minusDays(MAX_BARS.toLong())
        val fxRates = loadFx(fxFrom, asOf, failures)
        return MarketSyncResult(marketData = rows, fxRates = fxRates, failures = failures)
    }

    private suspend fun barsFor(
        asset: Asset,
        history: List<PriceBar>,
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        failures: MutableList<String>,
    ): List<DailyMarketData> {
        val relevant = history.filter { !it.date.isAfter(asOf) }
        if (relevant.isEmpty()) return emptyList()
        val stored = snapshot.marketData.filter { it.assetId == asset.id }
        if (isSpotOnlyCommodity(asset, relevant) && stored.size > 1) {
            return overlaySpotOnStoredSma(asset, relevant.last(), stored)
        }
        val storedFrom = (relevant.size - MAX_BARS).coerceAtLeast(0)
        val rating = ratingFor(asset, stored, asOf, failures)
        val storedByDate = stored.associateBy { it.date }
        return (storedFrom until relevant.size).map { index ->
            val bar = relevant[index]
            val closes = relevant.subList(0, index + 1).map { it.closeNative }
            DailyMarketData(
                assetId = asset.id,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = ratingOnBar(index == relevant.lastIndex, rating, storedByDate[bar.date]),
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

    private fun overlaySpotOnStoredSma(asset: Asset, bar: PriceBar, stored: List<DailyMarketData>): List<DailyMarketData> {
        val last = stored.maxByOrNull { it.date }
        return listOf(
            DailyMarketData(
                assetId = asset.id,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = AnalystRating.NONE,
                sma50 = last?.sma50,
                sma200 = last?.sma200,
            ),
        )
    }

    private fun isSpotOnlyCommodity(asset: Asset, bars: List<PriceBar>): Boolean = asset.assetType == AssetType.COMMODITY && bars.size == 1

    private suspend fun ratingFor(
        asset: Asset,
        stored: List<DailyMarketData>,
        asOf: LocalDate,
        failures: MutableList<String>,
    ): AnalystRating {
        val fresh =
            stored
                .filter { it.analystRating != AnalystRating.NONE && !it.date.isBefore(asOf.minusDays(OVERVIEW_MAX_AGE_DAYS)) }
                .maxByOrNull { it.date }
        if (fresh != null) return fresh.analystRating
        return runCatching { feed.analystRating(asset) }
            .onFailure { failures += "${asset.symbol} rating: ${it.message}" }
            .getOrDefault(AnalystRating.NONE)
    }

    private suspend fun loadFx(from: LocalDate, asOf: LocalDate, failures: MutableList<String>): List<CurrencyRate> {
        val history =
            runCatching { feed.eurPerUsdHistory(from, asOf) }
                .onFailure { failures += "FX: ${it.message}" }
                .getOrDefault(emptyList())
        if (history.isNotEmpty()) return history
        return runCatching { listOf(CurrencyRate(asOf, feed.eurPerUsd())) }
            .onFailure { failures += "FX: ${it.message}" }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_BARS: Int = 400
        const val OVERVIEW_MAX_AGE_DAYS: Long = 7
    }
}
