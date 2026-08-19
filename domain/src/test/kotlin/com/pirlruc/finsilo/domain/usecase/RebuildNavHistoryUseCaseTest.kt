package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.NavInputsFingerprint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RebuildNavHistoryUseCaseTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)

    @Test
    fun fingerprintChangesWhenFxValueChangesWithSameRowCount() {
        val base = snap()
        val other = base.copy(fxRates = listOf(CurrencyRate(asOf, bd("0.91"))))
        assertNotEquals(NavInputsFingerprint.of(base), NavInputsFingerprint.of(other))
    }

    @Test
    fun rebuildIsSkippedWhenFingerprintMatches() {
        val snapshot = snap()
        val first = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        assertFalse(first.skip)
        assertTrue(first.points.isNotEmpty())
        val second = RebuildNavHistoryUseCase()(snapshot, asOf, first.fingerprint, first.points)
        assertTrue(second.skip)
        assertEquals(first.points, second.points)
    }

    @Test
    fun laterAsOfAppendsDaysWithoutDroppingStoredPoints() {
        val snapshot = snap()
        val mid = LocalDate.of(2026, 8, 8)
        val first = RebuildNavHistoryUseCase()(snapshot, mid, null, emptyList())
        val extended = RebuildNavHistoryUseCase()(snapshot, asOf, first.fingerprint, first.points)
        assertFalse(extended.skip)
        assertEquals(first.points.first(), extended.points.first())
        assertEquals(asOf, extended.points.last().date)
        assertEquals(first.points.size + ChronoUnit.DAYS.between(mid, asOf).toInt(), extended.points.size)
    }

    @Test
    fun laterCashMovementRebuildsOnlyFromChangedDate() {
        val snapshot = snap()
        val first = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val extra =
            Transaction(
                id = "c-late",
                assetId = cash.id,
                date = asOf,
                type = TransactionType.DEPOSIT_CASH,
                quantity = bd("50"),
                unitPriceNative = BigDecimal.ONE,
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = BigDecimal.ONE,
                feesEur = BigDecimal.ZERO,
            )
        val updated = snapshot.copy(transactions = snapshot.transactions + extra)
        val rebuilt = RebuildNavHistoryUseCase()(updated, asOf, first.fingerprint, first.points, changedFrom = asOf)
        assertEquals(
            first.points.filter { it.date.isBefore(asOf) },
            rebuilt.points.filter { it.date.isBefore(asOf) },
        )
        assertEquals(0, first.points.last().valueEur.add(bd("50")).compareTo(rebuilt.points.last().valueEur))
    }

    @Test
    fun fingerprintIgnoresBigDecimalScale() {
        val base = snap()
        val scaled = base.copy(fxRates = listOf(CurrencyRate(asOf, bd("0.920"))))
        assertEquals(NavInputsFingerprint.of(base), NavInputsFingerprint.of(scaled))
    }

    @Test
    fun storedNavFeedsHistoryWithoutChangingLastPoint() {
        val snapshot = snap()
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val live = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf)
        val fromStore = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, rebuilt.points)
        assertEquals(live.points.last().date, fromStore.points.last().date)
        assertEquals(
            0,
            live.points.last().valueEur.compareTo(fromStore.points.last().valueEur),
        )
    }

    private fun snap(): PortfolioSnapshot {
        val start = LocalDate.of(2026, 8, 1)
        return PortfolioSnapshot(
            assets = listOf(etf, cash),
            transactions =
            listOf(
                Transaction(
                    id = "c0",
                    assetId = cash.id,
                    date = start,
                    type = TransactionType.DEPOSIT_CASH,
                    quantity = bd("10000"),
                    unitPriceNative = BigDecimal.ONE,
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = BigDecimal.ONE,
                    feesEur = BigDecimal.ZERO,
                ),
                Transaction(
                    id = "b1",
                    assetId = etf.id,
                    date = start,
                    type = TransactionType.BUY,
                    quantity = bd("10"),
                    unitPriceNative = bd("100"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("100"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            marketData = listOf(DailyMarketData(etf.id, asOf, bd("110"))),
            fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
            targets = emptyList(),
        )
    }
}
