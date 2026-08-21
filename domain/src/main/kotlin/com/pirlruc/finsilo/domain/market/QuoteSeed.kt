package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PriceBar
import java.time.LocalDate

/** Turns a probe history into stored daily rows, including locally computed SMAs. */
object QuoteSeed {
    fun fromBars(assetId: String, bars: List<PriceBar>, from: LocalDate? = null): List<DailyMarketData> {
        val relevant = if (from == null) bars else bars.filter { !it.date.isBefore(from) }
        if (relevant.isEmpty()) return emptyList()
        return relevant.indices.map { index ->
            val bar = relevant[index]
            val closes = relevant.subList(0, index + 1).map { it.closeNative }
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
