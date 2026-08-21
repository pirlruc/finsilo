package com.pirlruc.finsilo.ui.importcsv

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.market.HoldingHistory
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.usecase.ProbeMarketQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.QuoteProbeResult
import java.time.LocalDate

internal class QuoteDraftProber(private val feed: MarketFeed, private val today: () -> LocalDate) {
    private val bars = HashMap<String, List<PriceBar>>()
    private val probedQuote = HashMap<String, String>()
    private val outcomeByQuote = HashMap<String, QuoteProbeResult>()

    fun clear() {
        bars.clear()
        probedQuote.clear()
        outcomeByQuote.clear()
    }

    suspend fun probe(drafts: List<ImportSymbolDraft>): List<ImportSymbolDraft> {
        val probe = ProbeMarketQuoteUseCase(feed)
        val asOf = today()
        return drafts.map { draft -> probeOne(probe, asOf, draft) }
    }

    suspend fun store(repository: RoomPortfolioRepository, snapshot: PortfolioSnapshot, drafts: List<ImportSymbolDraft>) {
        drafts.forEach { draft ->
            val stored = bars[draft.key].orEmpty()
            if (stored.isEmpty()) return@forEach
            val asset = matchingAsset(snapshot, draft) ?: return@forEach
            val from = HoldingHistory.firstHeldOn(snapshot.transactions, asset.id)
            repository.upsertQuotes(QuoteSeed.fromBars(asset.id, stored, from), emptyList())
        }
    }

    private suspend fun probeOne(probe: ProbeMarketQuoteUseCase, asOf: LocalDate, draft: ImportSymbolDraft): ImportSymbolDraft {
        if (!draft.needsQuote) return draft
        val quote = draft.quoteSymbol.trim()
        if (probedQuote[draft.key] == quote) return draft
        val cacheKey = quote.uppercase()
        val outcome = outcomeByQuote[cacheKey] ?: probe(draft.asProbeAsset(), asOf).also { outcomeByQuote[cacheKey] = it }
        return applyOutcome(draft, quote, outcome)
    }

    private fun applyOutcome(draft: ImportSymbolDraft, quote: String, outcome: QuoteProbeResult): ImportSymbolDraft = when (outcome) {
        is QuoteProbeResult.Found -> {
            bars[draft.key] = outcome.bars
            probedQuote[draft.key] = quote
            draft.copy(quoteWarning = null)
        }
        is QuoteProbeResult.Missing -> {
            bars.remove(draft.key)
            probedQuote[draft.key] = quote
            draft.copy(quoteWarning = outcome.reason)
        }
    }
}
