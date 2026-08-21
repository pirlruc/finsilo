package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PriceBar
import java.time.LocalDate

/** Outcome of a one-shot GET-only quote probe before persisting a ticker. */
sealed interface QuoteProbeResult {
    data class Found(val bars: List<PriceBar>) : QuoteProbeResult

    data class Missing(val reason: String) : QuoteProbeResult
}

/**
 * Checks that a mark-to-market instrument has at least one usable close.
 * Locally valued names are not probed.
 */
class ProbeMarketQuoteUseCase(private val feed: MarketFeed) {
    suspend operator fun invoke(asset: Asset, asOf: LocalDate = LocalDate.now()): QuoteProbeResult {
        if (asset.locallyValued || asset.assetType.isLocallyValued) {
            return QuoteProbeResult.Found(emptyList())
        }
        val bars =
            runCatching { feed.dailyHistory(asset, asOf).filter { !it.date.isAfter(asOf) } }
                .getOrElse { return QuoteProbeResult.Missing("${asset.symbol}: ${it.message}") }
        if (bars.isEmpty()) return QuoteProbeResult.Missing("No current quote for ${asset.feedSymbol}.")
        return QuoteProbeResult.Found(bars)
    }
}
