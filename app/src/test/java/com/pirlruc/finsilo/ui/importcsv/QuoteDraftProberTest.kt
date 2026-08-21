package com.pirlruc.finsilo.ui.importcsv

import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceBar
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteDraftProberTest {
    private val asOf = LocalDate.of(2026, 8, 16)

    @Test
    fun sharedQuoteSymbolProbesOnce() = runBlocking {
        var calls = 0
        val feed =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> {
                    calls += 1
                    return listOf(PriceBar(asOf, BigDecimal.TEN))
                }
                override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
            }
        val prober = QuoteDraftProber(feed) { asOf }
        val first = draft("IE00B3RBWM25", "VWCE")
        val second = draft("US0378331005", "VWCE")
        val result = prober.probe(listOf(first, second))
        assertEquals(1, calls)
        assertEquals(2, result.size)
        assertNull(result[0].quoteWarning)
        assertNull(result[1].quoteWarning)
        val again = prober.probe(listOf(first.copy(name = "Vanguard")))
        assertEquals(1, calls)
        assertNull(again.single().quoteWarning)
    }

    private fun draft(key: String, quote: String) = ImportSymbolDraft(
        key = key,
        symbol = "VWCE",
        quoteSymbol = quote,
        name = "VWCE",
        assetType = AssetType.ETF,
        currency = Currency.EUR,
        isin = key,
        needsQuote = true,
    )
}
