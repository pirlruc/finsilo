package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.time.LocalDate

data class MarketSyncResult(
    val marketData: List<DailyMarketData>,
    val fxRates: List<CurrencyRate> = emptyList(),
    val failures: List<String>,
)

/**
 * Pulls GET-only public quotes and writes daily rows. SMA 50/200 are computed
 * locally from stored closes so Alpha Vantage's free quota is not spent on SMA.
 */
class SyncMarketDataUseCase(
    private val feed: MarketFeed,
) {
    suspend operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate = LocalDate.now()): MarketSyncResult {
        val failures = ArrayList<String>()
        val rows = ArrayList<DailyMarketData>()
        for (asset in snapshot.assets) {
            if (asset.locallyValued || asset.assetType == AssetType.CASH) continue
            val history = runCatching { feed.dailyHistory(asset) }
                .onFailure { failures += "${asset.symbol}: ${it.message}" }
                .getOrNull()
                ?: continue
            val relevant = history.filter { !it.date.isAfter(asOf) }
            if (relevant.isEmpty()) continue
            val storedFrom = (relevant.size - MAX_BARS).coerceAtLeast(0)
            val rating = runCatching { feed.analystRating(asset) }.getOrDefault(AnalystRating.NONE)
            for (index in storedFrom until relevant.size) {
                val bar = relevant[index]
                val closes = relevant.subList(0, index + 1).map { it.closeNative }
                rows += DailyMarketData(
                    assetId = asset.id,
                    date = bar.date,
                    closingPriceNative = bar.closeNative,
                    analystRating = if (index == relevant.lastIndex) rating else AnalystRating.NONE,
                    sma50 = MovingAverages.sma(closes, 50),
                    sma200 = MovingAverages.sma(closes, 200),
                )
            }
        }
        val fxFrom = snapshot.transactions.minOfOrNull { it.date } ?: asOf.minusDays(MAX_BARS.toLong())
        val fxRates = runCatching { feed.eurPerUsdHistory(fxFrom, asOf) }
            .onFailure { failures += "FX: ${it.message}" }
            .getOrDefault(emptyList())
            .ifEmpty {
                runCatching { listOf(CurrencyRate(asOf, feed.eurPerUsd())) }
                    .onFailure { failures += "FX: ${it.message}" }
                    .getOrDefault(emptyList())
            }
        return MarketSyncResult(marketData = rows, fxRates = fxRates, failures = failures)
    }

    companion object {
        private const val MAX_BARS: Int = 400
    }
}
