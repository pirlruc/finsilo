package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DashboardUseCasesTest {

    private val asOf = LocalDate.of(2026, 8, 16)
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)

    @Test
    fun historyStartsAtFirstTransactionAndIncludesAsOf() {
        val snapshot = snapshotWithBuy(LocalDate.of(2026, 8, 10))
        val report = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf)
        assertEquals(LocalDate.of(2026, 8, 10), report.from)
        assertEquals(asOf, report.to)
        assertEquals(asOf, report.points.last().date)
        assertTrue(report.points.size >= 2)
        assertTrue(report.points.zipWithNext().all { (a, b) -> a.date.isBefore(b.date) })
    }

    @Test
    fun oneMonthRangeDoesNotStartBeforeFirstTransaction() {
        val snapshot = snapshotWithBuy(LocalDate.of(2026, 8, 1))
        val report = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ONE_MONTH, asOf)
        assertEquals(LocalDate.of(2026, 8, 1), report.from)
    }

    @Test
    fun historyDownsamplesWhenTheWindowExceedsMaxPoints() {
        val snapshot = snapshotWithBuy(LocalDate.of(2026, 1, 1))
        val report = GetPortfolioHistoryUseCase(maxPoints = 10)(snapshot, HistoryRange.ALL, asOf)
        assertTrue(report.points.size <= 12, "expected downsample, got ${report.points.size}")
        assertEquals(asOf, report.points.last().date)
    }

    @Test
    fun goldenCrossMatchesRfcInequality() {
        assertEquals(
            TechnicalCross.GOLDEN,
            GetMarketSignalsUseCase.detectCross(bd("10"), bd("12"), bd("13"), bd("12")),
        )
        assertEquals(
            TechnicalCross.DEATH,
            GetMarketSignalsUseCase.detectCross(bd("12"), bd("10"), bd("9"), bd("10")),
        )
        assertEquals(null, GetMarketSignalsUseCase.detectCross(bd("13"), bd("10"), bd("14"), bd("10")))
    }

    @Test
    fun ratingChangeAndGoldenCrossAppearOnHeldAssets() {
        val yesterday = asOf.minusDays(1)
        val snapshot = PortfolioSnapshot(
            assets = listOf(apple, cash),
            transactions = listOf(
                Transaction(
                    id = "c",
                    assetId = cash.id,
                    date = asOf.minusDays(10),
                    type = TransactionType.DEPOSIT_CASH,
                    quantity = bd("5000"),
                    unitPriceNative = BigDecimal.ONE,
                    exchangeRateAtExecution = bd("1.10"),
                    unitPriceEur = BigDecimal.ONE,
                    feesEur = BigDecimal.ZERO,
                ),
                Transaction(
                    id = "b",
                    assetId = apple.id,
                    date = asOf.minusDays(10),
                    type = TransactionType.BUY,
                    quantity = bd("5"),
                    unitPriceNative = bd("100"),
                    exchangeRateAtExecution = bd("1.10"),
                    unitPriceEur = bd("90.90909091"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            marketData = listOf(
                DailyMarketData(apple.id, yesterday, bd("150"), AnalystRating.HOLD, bd("10"), bd("12")),
                DailyMarketData(apple.id, asOf, bd("155"), AnalystRating.BUY, bd("13"), bd("12")),
            ),
            fxRates = listOf(CurrencyRate(asOf, bd("1.10"))),
            targets = emptyList(),
        )
        val signals = GetMarketSignalsUseCase()(snapshot, asOf)
        val appleSignal = signals.single { it.asset.id == apple.id }
        assertTrue(appleSignal.ratingChanged)
        assertEquals(AnalystRating.HOLD, appleSignal.previousRating)
        assertEquals(AnalystRating.BUY, appleSignal.rating)
        assertEquals(TechnicalCross.GOLDEN, appleSignal.cross)
    }

    @Test
    fun samplePortfolioDashboardIsInternallyConsistent() {
        val snapshot = SamplePortfolioFactory.create(asOf)
        val dashboard = GetDashboardUseCase()(snapshot, HistoryRange.ALL, asOf)
        assertTrue(dashboard.allocation.totalValueEur > BigDecimal.ZERO)
        val weightSum = dashboard.allocation.slices.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.weightPercent) }
        assertEquals(0, bd("100").compareTo(weightSum.setScale(1, java.math.RoundingMode.HALF_EVEN)))
        assertTrue(dashboard.history.points.isNotEmpty())
        assertEquals(dashboard.allocation.totalValueEur.setScale(4, java.math.RoundingMode.HALF_EVEN),
            dashboard.history.points.last().valueEur.setScale(4, java.math.RoundingMode.HALF_EVEN))
        assertTrue(dashboard.signals.any { it.asset.symbol == "AAPL" })
        assertTrue(dashboard.signals.any { it.cross == TechnicalCross.GOLDEN })
        assertTrue(dashboard.allocation.slices.any { it.assetType == AssetType.ETF })
        assertTrue(dashboard.allocation.slices.any { it.assetType == AssetType.CT })
        assertTrue(dashboard.allocation.slices.any { it.assetType == AssetType.COMMODITY })
        assertTrue(dashboard.yoc.any { it.asset.symbol == "AAPL" && it.paymentsPerYear == 4 })
    }

    private fun snapshotWithBuy(buyDate: LocalDate): PortfolioSnapshot {
        val vwce = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
        return PortfolioSnapshot(
            assets = listOf(vwce, cash),
            transactions = listOf(
                Transaction(
                    id = "c",
                    assetId = cash.id,
                    date = buyDate,
                    type = TransactionType.DEPOSIT_CASH,
                    quantity = bd("10000"),
                    unitPriceNative = BigDecimal.ONE,
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = BigDecimal.ONE,
                    feesEur = BigDecimal.ZERO,
                ),
                Transaction(
                    id = "b",
                    assetId = vwce.id,
                    date = buyDate,
                    type = TransactionType.BUY,
                    quantity = bd("10"),
                    unitPriceNative = bd("100"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("100"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            marketData = generateSequence(buyDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(asOf) }
                .map { DailyMarketData(vwce.id, it, bd("110")) }
                .toList(),
            fxRates = emptyList(),
            targets = emptyList(),
        )
    }
}
