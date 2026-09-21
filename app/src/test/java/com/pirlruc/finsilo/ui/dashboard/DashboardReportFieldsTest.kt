package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.HistoryReport
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.RelativeToAverage
import com.pirlruc.finsilo.domain.model.TwrReport
import com.pirlruc.finsilo.domain.model.TwrSplit
import com.pirlruc.finsilo.domain.model.TwrSubPeriod
import com.pirlruc.finsilo.domain.model.YocReport
import com.pirlruc.finsilo.ui.formatEur
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardReportFieldsTest {
    private val vwce = Asset("vwce", "VWCE.DE", "All-World", AssetType.ETF, Currency.EUR)
    private val asOf = LocalDate.of(2026, 8, 16)

    @Test
    fun holdingDetailsIncludeEurUnitAndFifoCost() {
        val holding =
            HoldingValuation(
                asset = vwce,
                quantity = BigDecimal("2"),
                priceEur = BigDecimal("110"),
                valueEur = BigDecimal("220"),
                costEur = BigDecimal("200"),
                unrealizedPnlEur = BigDecimal("20"),
            )
        val details = holdingDetails(holding)
        assertTrue(details[0].contains("All-World"))
        assertTrue(details[0].contains("qty 2"))
        assertEquals("EUR unit ${formatEur(BigDecimal("110"))}", details[1])
        assertEquals("FIFO cost ${formatEur(BigDecimal("200"))}", details[2])
    }

    @Test
    fun yocLineIncludesRemainingCost() {
        val line =
            yocLine(
                YocReport(
                    asset = vwce,
                    remainingCostEur = BigDecimal("1000"),
                    ttmPercent = BigDecimal("2.0"),
                    lastTimesFrequencyPercent = BigDecimal("2.4"),
                    paymentsPerYear = 4,
                ),
            )
        assertTrue(line.contains("VWCE.DE"))
        assertTrue(line.contains(formatEur(BigDecimal("1000"))))
        assertTrue(line.contains("TTM"))
    }

    @Test
    fun historyWindowAndTwrCaptionUseReportDates() {
        val history = HistoryReport(from = asOf.minusMonths(3), to = asOf, points = emptyList())
        assertEquals("2026-05-16 – 2026-08-16", historyWindowLabel(history))
        assertEquals("0 sub-period(s)", twrCaption(TwrReport(BigDecimal.ZERO, emptyList())))
        val caption =
            twrCaption(
                TwrReport(
                    twrPercent = BigDecimal.ONE,
                    subPeriods =
                    listOf(
                        TwrSubPeriod(asOf.minusDays(10), asOf.minusDays(5), BigDecimal.ONE, TwrSplit.EXTERNAL_BUY),
                        TwrSubPeriod(asOf.minusDays(5), asOf, BigDecimal.ONE, TwrSplit.WITHDRAWAL),
                    ),
                ),
            )
        assertTrue(caption.contains("2 sub-period(s)"))
        assertTrue(caption.contains("2026-08-06–2026-08-16"))
        assertTrue(caption.contains("external buy"))
        assertTrue(caption.contains("withdrawal"))
    }

    @Test
    fun signalCaptionIncludesAsOfAndSmaLevels() {
        val caption =
            signalCaption(
                MarketSignal(
                    asset = vwce,
                    asOf = asOf,
                    rating = AnalystRating.BUY,
                    previousRating = AnalystRating.HOLD,
                    priceNative = BigDecimal("110"),
                    sma50 = BigDecimal("100"),
                    sma200 = BigDecimal("90"),
                    vsSma50 = RelativeToAverage.ABOVE,
                    vsSma200 = RelativeToAverage.ABOVE,
                    cross = null,
                ),
            )
        assertEquals("2026-08-16", caption.first())
        assertTrue(caption.any { it.startsWith("SMA50 ") })
        assertTrue(caption.any { it.startsWith("SMA200 ") })
        assertTrue(caption.any { it.contains("rating") })
    }

    @Test
    fun brokerSourceLabelsAreStable() {
        assertEquals("Trading 212", com.pirlruc.finsilo.domain.model.BrokerSource.TRADING_212.label)
        assertEquals("DEGIRO", com.pirlruc.finsilo.domain.model.BrokerSource.DEGIRO.label)
        assertEquals("Revolut", com.pirlruc.finsilo.domain.model.BrokerSource.REVOLUT.label)
        assertEquals("Manual", com.pirlruc.finsilo.domain.model.BrokerSource.MANUAL.label)
    }
}
