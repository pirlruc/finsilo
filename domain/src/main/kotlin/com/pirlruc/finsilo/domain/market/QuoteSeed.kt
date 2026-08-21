package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PriceBar

/** Turns a probe history into stored daily rows, including locally computed SMAs. */
object QuoteSeed {
    private const val MAX_BARS: Int = 400

    fun fromBars(assetId: String, bars: List<PriceBar>): List<DailyMarketData> {
        if (bars.isEmpty()) return emptyList()
        val from = (bars.size - MAX_BARS).coerceAtLeast(0)
        return (from until bars.size).map { index ->
            val bar = bars[index]
            val closes = bars.subList(0, index + 1).map { it.closeNative }
            DailyMarketData(
                assetId = assetId,
                date = bar.date,
                closingPriceNative = bar.closeNative,
                sma50 = MovingAverages.sma(closes, 50),
                sma200 = MovingAverages.sma(closes, 200),
            )
        }
    }
}
