package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.HoldingHistory
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.market.QuoteSyncPlanner
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
 * Names whose last bar is already [asOf] are skipped; the rest run oldest-first.
 */
class SyncMarketDataUseCase(private val feed: MarketFeed) {
    suspend operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate = LocalDate.now()): MarketSyncResult {
        val failures = ArrayList<String>()
        val rows = ArrayList<DailyMarketData>()
        for (asset in orderedMarketable(snapshot)) {
            rows += syncAsset(asset, snapshot, asOf, failures)
        }
        val fxFrom = snapshot.transactions.minOfOrNull { it.date } ?: asOf
        val fxRates = loadFx(fxFrom, asOf, failures)
        return MarketSyncResult(marketData = rows, fxRates = fxRates, failures = failures)
    }

    private fun orderedMarketable(snapshot: PortfolioSnapshot): List<Asset> {
        val marketable = snapshot.assets.filterNot { it.locallyValued || it.assetType == AssetType.CASH }
        return QuoteSyncPlanner.oldestFirst(marketable) { asset ->
            QuoteSyncPlanner.lastBarDate(snapshot.marketData, asset.id)
        }
    }

    private suspend fun syncAsset(
        asset: Asset,
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        failures: MutableList<String>,
    ): List<DailyMarketData> {
        val stored = snapshot.marketData.filter { it.assetId == asset.id }
        val last = QuoteSyncPlanner.lastBarDate(stored, asset.id)
        if (QuoteSyncPlanner.isFresh(last, asOf)) {
            return refreshRatingOnly(asset, stored, asOf, failures)
        }
        val history =
            runCatching { feed.dailyHistory(asset, asOf) }
                .onFailure { failures += "${asset.symbol}: ${it.message}" }
                .getOrNull()
        return if (history == null) emptyList() else barsFor(asset, history, snapshot, asOf, failures)
    }

    private suspend fun refreshRatingOnly(
        asset: Asset,
        stored: List<DailyMarketData>,
        asOf: LocalDate,
        failures: MutableList<String>,
    ): List<DailyMarketData> {
        val latest = stored.maxBy { it.date }
        val rating = ratingFor(asset, stored, asOf, failures)
        if (rating == latest.analystRating) return emptyList()
        return listOf(latest.copy(analystRating = rating))
    }

    private suspend fun barsFor(
        asset: Asset,
        history: List<PriceBar>,
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        failures: MutableList<String>,
    ): List<DailyMarketData> {
        val relevant = heldBars(asset, history, snapshot, asOf)
        if (relevant.isEmpty()) return emptyList()
        val stored = snapshot.marketData.filter { it.assetId == asset.id }
        if (isSpotOnlyCommodity(asset, relevant) && stored.size > 1) {
            return overlaySpotOnStoredSma(asset, relevant.last(), stored)
        }
        val rating = ratingFor(asset, stored, asOf, failures)
        val storedByDate = stored.associateBy { it.date }
        return relevant.indices.map { index ->
            barRow(asset, relevant, index, rating, storedByDate)
        }
    }

    private fun heldBars(asset: Asset, history: List<PriceBar>, snapshot: PortfolioSnapshot, asOf: LocalDate): List<PriceBar> {
        val from = HoldingHistory.firstHeldOn(snapshot.transactions, asset.id)
        return history.filter { bar ->
            !bar.date.isAfter(asOf) && (from == null || !bar.date.isBefore(from))
        }
    }

    private fun barRow(
        asset: Asset,
        relevant: List<PriceBar>,
        index: Int,
        rating: AnalystRating,
        storedByDate: Map<LocalDate, DailyMarketData>,
    ): DailyMarketData {
        val bar = relevant[index]
        val closes = relevant.subList(0, index + 1).map { it.closeNative }
        return DailyMarketData(
            assetId = asset.id,
            date = bar.date,
            closingPriceNative = bar.closeNative,
            analystRating = ratingOnBar(index == relevant.lastIndex, rating, storedByDate[bar.date]),
            sma50 = MovingAverages.sma(closes, 50),
            sma200 = MovingAverages.sma(closes, 200),
        )
    }

    private fun ratingOnBar(isLatest: Boolean, latest: AnalystRating, stored: DailyMarketData?): AnalystRating {
        if (isLatest) return latest
        val previous = stored?.analystRating
        return if (previous != null && previous != AnalystRating.NONE) previous else AnalystRating.NONE
    }

    private fun overlaySpotOnStoredSma(asset: Asset, bar: PriceBar, stored: List<DailyMarketData>): List<DailyMarketData> {
        val last = stored.maxBy { it.date }
        return listOf(
            DailyMarketData(
                assetId = asset.id,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                analystRating = AnalystRating.NONE,
                sma50 = last.sma50,
                sma200 = last.sma200,
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
        const val OVERVIEW_MAX_AGE_DAYS: Long = 7
    }
}
