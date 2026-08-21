package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.startDate
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryBoundsTest {
    private val asOf = LocalDate.of(2026, 8, 16)

    @Test
    fun firstHeldOnPrefersFirstBuy() {
        val txs =
            listOf(
                tx("d", "cash", asOf.minusDays(10), TransactionType.DEPOSIT_CASH),
                tx("s", "aapl", asOf.minusDays(2), TransactionType.SELL),
                tx("b", "aapl", asOf.minusDays(5), TransactionType.BUY),
            )
        assertEquals(asOf.minusDays(5), HoldingHistory.firstHeldOn(txs, "aapl"))
        assertEquals(asOf.minusDays(10), HoldingHistory.firstHeldOn(txs, "cash"))
        assertEquals(null, HoldingHistory.firstHeldOn(txs, "missing"))
    }

    @Test
    fun thinIncreasesStepInsteadOfDroppingAWindow() {
        val values = (1..10).toList()
        assertEquals(values, HistoryPeriodicity.thin(values, 20, 10))
        val thinned = HistoryPeriodicity.thin(values, 3, 10)
        assertEquals(listOf(1, 5, 9, 10), thinned)
        assertEquals(1, thinned.first())
        assertEquals(10, thinned.last())
    }

    @Test
    fun quoteMergeReplacesByKeyAndAddsMissingLatest() {
        val old = listOf(DailyMarketData("a", asOf.minusDays(1), BigDecimal.ONE), DailyMarketData("b", asOf, BigDecimal.TEN))
        val incoming = listOf(DailyMarketData("a", asOf.minusDays(1), BigDecimal.TWO))
        val merged = QuoteMerge.market(old, incoming)
        assertEquals(BigDecimal.TWO, merged.single { it.assetId == "a" }.closingPriceNative)
        assertEquals(1, QuoteMerge.fx(listOf(CurrencyRate(asOf, BigDecimal.ONE)), emptyList()).size)
        val ranged = listOf(DailyMarketData("a", asOf, BigDecimal.ONE))
        val latest = listOf(DailyMarketData("a", asOf.minusDays(3), BigDecimal.ONE), DailyMarketData("b", asOf.minusYears(1), BigDecimal.TEN))
        val combined = QuoteMerge.plusLatest(ranged, latest)
        assertEquals(setOf("a", "b"), combined.map { it.assetId }.toSet())
        assertEquals(1, combined.count { it.assetId == "a" })
    }

    @Test
    fun historyRangeStartIsNotBeforeFirstLedgerDate() {
        val first = LocalDate.of(2026, 6, 1)
        assertEquals(asOf.minusMonths(1), HistoryRange.ONE_MONTH.startDate(asOf, first))
        assertEquals(first, HistoryRange.THREE_MONTHS.startDate(asOf, first))
        assertEquals(LocalDate.of(2026, 1, 1), HistoryRange.YTD.startDate(asOf, LocalDate.of(2025, 1, 1)))
        assertEquals(first, HistoryRange.ALL.startDate(asOf, first))
    }

    @Test
    fun quoteSeedDropsBarsBeforeFrom() {
        val bars =
            listOf(
                PriceBar(asOf.minusDays(3), BigDecimal.ONE),
                PriceBar(asOf.minusDays(1), BigDecimal.TEN),
                PriceBar(asOf, BigDecimal("11")),
            )
        val rows = QuoteSeed.fromBars("aapl", bars, asOf.minusDays(1))
        assertEquals(listOf(asOf.minusDays(1), asOf), rows.map { it.date })
        assertTrue(QuoteSeed.fromBars("aapl", bars, asOf.plusDays(1)).isEmpty())
    }

    private fun tx(id: String, assetId: String, date: LocalDate, type: TransactionType) = Transaction(
        id = id,
        assetId = assetId,
        date = date,
        type = type,
        quantity = BigDecimal.ONE,
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
    )
}
