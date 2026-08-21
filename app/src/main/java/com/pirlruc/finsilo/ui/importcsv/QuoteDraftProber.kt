package com.pirlruc.finsilo.ui.importcsv

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.usecase.ProbeMarketQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.QuoteProbeResult
import java.time.LocalDate

internal class QuoteDraftProber(private val feed: MarketFeed, private val today: () -> LocalDate) {
    private val bars = HashMap<String, List<PriceBar>>()

    fun clear() {
        bars.clear()
    }

    suspend fun probe(drafts: List<ImportSymbolDraft>): List<ImportSymbolDraft> {
        val probe = ProbeMarketQuoteUseCase(feed)
        val asOf = today()
        val next = drafts.map { draft -> probeOne(probe, asOf, draft) }
        return next
    }

    suspend fun store(repository: RoomPortfolioRepository, snapshot: PortfolioSnapshot, drafts: List<ImportSymbolDraft>) {
        drafts.forEach { draft ->
            val stored = bars[draft.key].orEmpty()
            if (stored.isEmpty()) return@forEach
            val asset = matchingAsset(snapshot, draft) ?: return@forEach
            repository.upsertQuotes(QuoteSeed.fromBars(asset.id, stored), emptyList())
        }
    }

    private suspend fun probeOne(probe: ProbeMarketQuoteUseCase, asOf: LocalDate, draft: ImportSymbolDraft): ImportSymbolDraft {
        if (!draft.needsQuote) return draft
        return when (val outcome = probe(draft.asProbeAsset(), asOf)) {
            is QuoteProbeResult.Found -> {
                bars[draft.key] = outcome.bars
                draft.copy(quoteWarning = null)
            }
            is QuoteProbeResult.Missing -> draft.copy(quoteWarning = outcome.reason)
        }
    }
}
