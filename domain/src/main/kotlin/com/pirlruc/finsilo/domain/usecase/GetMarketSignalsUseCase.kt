package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.RelativeToAverage
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.time.LocalDate

/**
 * Surfaces analyst ratings and moving-average context for currently held marketable assets.
 * Golden/Death cross uses the RFC rule on consecutive SMA 50/200 observations.
 */
class GetMarketSignalsUseCase(
    private val ledger: PositionLedger = PositionLedger(),
) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): List<MarketSignal> {
        val marketByAsset = ledger.indexMarket(snapshot.marketData)
        val txsByAsset = ledger.transactionsOnOrBefore(snapshot.transactions, asOf).groupBy { it.assetId }

        return snapshot.assets.mapNotNull { asset ->
            if (asset.assetType.isLocallyValued || asset.assetType == AssetType.CASH) return@mapNotNull null
            val txs = txsByAsset[asset.id].orEmpty()
            if (txs.isEmpty()) return@mapNotNull null
            if (ledger.position(txs).quantity.signum() == 0) return@mapNotNull null

            val series = marketByAsset[asset.id].orEmpty().filter { !it.date.isAfter(asOf) }
            val today = series.lastOrNull() ?: return@mapNotNull null
            val yesterday = series.getOrNull(series.lastIndex - 1)
            MarketSignal(
                asset = asset,
                asOf = today.date,
                rating = today.analystRating,
                previousRating = yesterday?.analystRating,
                priceNative = today.closingPriceNative,
                sma50 = today.sma50,
                sma200 = today.sma200,
                vsSma50 = relative(today.closingPriceNative, today.sma50),
                vsSma200 = relative(today.closingPriceNative, today.sma200),
                cross = detectCross(yesterday?.sma50, yesterday?.sma200, today.sma50, today.sma200),
            )
        }.sortedBy { it.asset.symbol }
    }

    private fun relative(price: java.math.BigDecimal, average: java.math.BigDecimal?): RelativeToAverage? {
        if (average == null) return null
        return when {
            price > average -> RelativeToAverage.ABOVE
            price < average -> RelativeToAverage.BELOW
            else -> null
        }
    }

    companion object {
        fun detectCross(
            yesterdaySma50: java.math.BigDecimal?,
            yesterdaySma200: java.math.BigDecimal?,
            todaySma50: java.math.BigDecimal?,
            todaySma200: java.math.BigDecimal?,
        ): TechnicalCross? {
            if (yesterdaySma50 == null || yesterdaySma200 == null || todaySma50 == null || todaySma200 == null) {
                return null
            }
            val golden = todaySma50 > todaySma200 && yesterdaySma50 <= yesterdaySma200
            val death = todaySma50 < todaySma200 && yesterdaySma50 >= yesterdaySma200
            return when {
                golden -> TechnicalCross.GOLDEN
                death -> TechnicalCross.DEATH
                else -> null
            }
        }
    }
}
