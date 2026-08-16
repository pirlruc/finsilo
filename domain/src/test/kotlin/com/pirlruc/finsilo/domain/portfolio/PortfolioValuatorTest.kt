package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

private fun assertMoney(expected: String, actual: BigDecimal?, label: String) {
    requireNotNull(actual) { "$label was null" }
    assertEquals(
        0,
        BigDecimal(expected).compareTo(actual),
        "$label expected $expected but was ${actual.toPlainString()}",
    )
}

class PortfolioValuatorTest {

    private val valuator = PortfolioValuator()
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
    private val vwce = Asset("vwce", "VWCE.DE", "All-World", AssetType.ETF, Currency.EUR)
    private val ct = Asset("ct", "CT", "Certificados", AssetType.CT, Currency.EUR)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val asOf = LocalDate.of(2026, 8, 16)

    @Test
    fun usdHoldingIsConvertedWithUsdPerEurRate() {
        val snapshot = snapshot(
            assets = listOf(apple, cash),
            transactions = listOf(
                cashIn("t0", LocalDate.of(2026, 1, 1), bd("2000")),
                buy("t1", apple.id, LocalDate.of(2026, 1, 2), bd("10"), bd("110"), Currency.USD, bd("1.10"), fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(apple.id, asOf, bd("220"))),
            fx = listOf(CurrencyRate(asOf, bd("1.10"))),
        )

        val report = valuator.allocation(snapshot, asOf)
        val stock = report.holdings.single { it.asset.id == apple.id }
        // 220 USD / 1.10 = 200 EUR; 10 shares = 2000 EUR
        assertMoney("200", stock.priceEur, "price")
        assertMoney("2000", stock.valueEur, "value")
        // cost: 10 * (110/1.10) = 1000 EUR; unrealized 1000
        assertMoney("1000", stock.unrealizedPnlEur, "unrealized")
    }

    @Test
    fun sellUsesMovingAverageCostAndDoesNotChangeRemainingAverage() {
        val ledger = PositionLedger()
        val txs = listOf(
            buy("b1", apple.id, LocalDate.of(2026, 1, 1), bd("10"), bd("100"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
            buy("b2", apple.id, LocalDate.of(2026, 1, 2), bd("10"), bd("200"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
            sell("s1", apple.id, LocalDate.of(2026, 1, 3), bd("5"), bd("180")),
        )
        val lot = ledger.position(txs)
        assertMoney("15", lot.quantity, "qty")
        // remaining cost = 3000 * 15/20 = 2250; avg stays 150
        assertMoney("150", lot.averageCostEur, "avg")
    }

    @Test
    fun depositAndInterestAreLocallyValuedWithoutMarketPrice() {
        val snapshot = snapshot(
            assets = listOf(ct, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("5000")),
                buy("b1", ct.id, LocalDate.of(2026, 1, 2), bd("3000"), bd("1"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
                Transaction(
                    id = "i1",
                    assetId = ct.id,
                    date = LocalDate.of(2026, 6, 1),
                    type = TransactionType.INTEREST,
                    quantity = bd("1"),
                    unitPriceNative = bd("40"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("40"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            market = emptyList(),
            fx = emptyList(),
        )
        val report = valuator.allocation(snapshot, asOf)
        val holding = report.holdings.single { it.asset.id == ct.id }
        assertMoney("3040", holding.valueEur, "ct value")
        assertEquals(AssetType.CT, holding.asset.assetType)
        assertTrue(report.slices.any { it.assetType == AssetType.CT })
    }

    @Test
    fun allocationPercentsSumToOneHundredWhenPortfolioHasValue() {
        val snapshot = snapshot(
            assets = listOf(vwce, ct, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("10000")),
                buy("b1", vwce.id, LocalDate.of(2026, 1, 2), bd("50"), bd("100"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
                buy("b2", ct.id, LocalDate.of(2026, 1, 3), bd("2000"), bd("1"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(vwce.id, asOf, bd("120"))),
            fx = emptyList(),
            targets = listOf(
                TargetAllocation(AssetType.ETF, bd("40")),
                TargetAllocation(AssetType.CT, bd("20")),
                TargetAllocation(AssetType.CASH, bd("40")),
            ),
        )
        val report = valuator.allocation(snapshot, asOf)
        val sum = report.slices.fold(BigDecimal.ZERO) { acc, slice -> acc.add(slice.weightPercent) }
        assertEquals(0, bd("100").compareTo(sum.setScale(2, java.math.RoundingMode.HALF_EVEN)))
        val etf = report.slices.single { it.assetType == AssetType.ETF }
        // ETF = 50 * 120 = 6000; CT = 2000; leftover cash = 10000 - 5000 - 2000 = 3000; total 11000
        assertEquals(0, bd("6000").compareTo(etf.valueEur))
        assertEquals(0, bd("2000").compareTo(report.slices.single { it.assetType == AssetType.CT }.valueEur))
        assertTrue(etf.driftPercent != null)
        assertTrue(etf.exceedsDriftBand)
    }

    @Test
    fun emptySnapshotHasZeroTotal() {
        val report = valuator.allocation(PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), asOf)
        assertEquals(0, BigDecimal.ZERO.compareTo(report.totalValueEur))
        assertTrue(report.slices.isEmpty())
    }

    @Test
    fun missingUsdFxFallsBackToOneAndStillProducesAValue() {
        val snapshot = snapshot(
            assets = listOf(apple),
            transactions = listOf(
                cashIn("c0", asOf.minusDays(1), bd("500")),
                buy("t1", apple.id, asOf, bd("2"), bd("100"), Currency.USD, bd("1.10"), fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(apple.id, asOf, bd("100"))),
            fx = emptyList(),
        )
        val stock = valuator.valueHoldings(snapshot, asOf).single { it.asset.id == apple.id }
        // fallback usdPerEur=1 so 2*100 = 200
        assertEquals(0, bd("200").compareTo(stock.valueEur))
    }

    private fun snapshot(
        assets: List<Asset>,
        transactions: List<Transaction>,
        market: List<DailyMarketData>,
        fx: List<CurrencyRate>,
        targets: List<TargetAllocation> = emptyList(),
    ) = PortfolioSnapshot(assets, transactions, market, fx, targets)

    private fun cashIn(id: String, date: LocalDate, amount: BigDecimal) =
        Transaction(
            id = id,
            assetId = cash.id,
            date = date,
            type = TransactionType.DEPOSIT_CASH,
            quantity = amount,
            unitPriceNative = BigDecimal.ONE,
            exchangeRateAtExecution = BigDecimal.ONE,
            unitPriceEur = BigDecimal.ONE,
            feesEur = BigDecimal.ZERO,
        )

    private fun buy(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        native: BigDecimal,
        currency: Currency,
        usdPerEur: BigDecimal,
        fees: BigDecimal = bd("1.50"),
    ): Transaction {
        val eur = MoneyMath.toEur(native, currency, usdPerEur)
        return Transaction(id, assetId, date, TransactionType.BUY, qty, native, usdPerEur, eur, fees)
    }

    private fun sell(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        nativeEur: BigDecimal,
    ) = Transaction(
        id = id,
        assetId = assetId,
        date = date,
        type = TransactionType.SELL,
        quantity = qty,
        unitPriceNative = nativeEur,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = nativeEur,
        feesEur = BigDecimal.ZERO,
    )
}
