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
    fun usdHoldingIsConvertedWithEurPerUsdRate() {
        val eurPerUsd = bd("0.50")
        val snapshot = snapshot(
            assets = listOf(apple, cash),
            transactions = listOf(
                cashIn("t0", LocalDate.of(2026, 1, 1), bd("2000")),
                buy("t1", apple.id, LocalDate.of(2026, 1, 2), bd("10"), bd("110"), Currency.USD, eurPerUsd, fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(apple.id, asOf, bd("220"))),
            fx = listOf(CurrencyRate(asOf, eurPerUsd)),
        )

        val report = valuator.allocation(snapshot, asOf)
        val stock = report.holdings.single { it.asset.id == apple.id }
        // 220 USD * 0.50 EUR/USD = 110 EUR; 10 shares = 1100 EUR; cost 10*110*0.50 = 550
        assertMoney("110", stock.priceEur, "price")
        assertMoney("1100", stock.valueEur, "value")
        assertMoney("550", stock.unrealizedPnlEur, "unrealized")
    }

    @Test
    fun sellConsumesOldestLotsFirst() {
        val ledger = PositionLedger()
        val txs = listOf(
            buy("b1", apple.id, LocalDate.of(2026, 1, 1), bd("10"), bd("100"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
            buy("b2", apple.id, LocalDate.of(2026, 1, 2), bd("10"), bd("200"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
            sell("s1", apple.id, LocalDate.of(2026, 1, 3), bd("5"), bd("180")),
        )
        val lot = ledger.position(txs)
        assertMoney("15", lot.quantity, "qty")
        // FIFO remaining: 5 @ 100 + 10 @ 200 = 2500; avg 166.666...
        assertMoney("2500", lot.remainingCostEur, "cost")
    }

    @Test
    fun commodityIsMarkedToMarketAgainstFifoCost() {
        val gold = Asset("gold", "XAU", "Gold", AssetType.COMMODITY, Currency.USD)
        val eurPerUsd = bd("0.92")
        val snapshot = snapshot(
            assets = listOf(gold, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("10000")),
                buy("b1", gold.id, LocalDate.of(2026, 1, 2), bd("2"), bd("2000"), Currency.USD, eurPerUsd, fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(gold.id, asOf, bd("2500"))),
            fx = listOf(CurrencyRate(asOf, eurPerUsd)),
        )
        val holding = valuator.allocation(snapshot, asOf).holdings.single { it.asset.id == gold.id }
        // 2 * 2500 USD * 0.92 = 4600 EUR; cost 2 * 2000 * 0.92 = 3680; pnl 920
        assertMoney("4600", holding.valueEur, "value")
        assertMoney("3680", holding.costEur, "cost")
        assertMoney("920", holding.unrealizedPnlEur, "pnl")
        assertEquals(AssetType.COMMODITY, holding.asset.assetType)
    }

    @Test
    fun unlistedPprInterestStaysInNavNotCash() {
        val ppr = Asset(
            "ppr",
            "PTYAAAA00001",
            "PPR Moderado",
            AssetType.PPR,
            Currency.EUR,
            isin = "PTYAAAA00001",
            quoteSymbol = null,
        )
        val snapshot = snapshot(
            assets = listOf(ppr, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("5000")),
                buy("b1", ppr.id, LocalDate.of(2026, 1, 2), bd("40"), bd("12.50"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
                Transaction(
                    id = "i1",
                    assetId = ppr.id,
                    date = LocalDate.of(2026, 6, 1),
                    type = TransactionType.INTEREST,
                    quantity = bd("1"),
                    unitPriceNative = bd("15"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("15"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            market = emptyList(),
            fx = emptyList(),
        )
        val report = valuator.allocation(snapshot, asOf)
        val holding = report.holdings.single { it.asset.id == ppr.id }
        assertTrue(ppr.locallyValued)
        assertMoney("515", holding.valueEur, "ppr value")
        assertMoney("4500", report.cashEur, "cash after buy, interest stays in PPR")
        assertTrue(report.slices.any { it.assetType == AssetType.PPR })
    }

    @Test
    fun listedPprUsesMarketPriceAndPaysInterestToCash() {
        val listed = Asset(
            "ppr-listed",
            "PPR Moderado",
            "PPR Moderado",
            AssetType.PPR,
            Currency.EUR,
            quoteSymbol = "VWCE.DE",
        )
        val snapshot = snapshot(
            assets = listOf(listed, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("5000")),
                buy("b1", listed.id, LocalDate.of(2026, 1, 2), bd("10"), bd("100"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
                Transaction(
                    id = "i1",
                    assetId = listed.id,
                    date = LocalDate.of(2026, 6, 1),
                    type = TransactionType.INTEREST,
                    quantity = bd("1"),
                    unitPriceNative = bd("20"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("20"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            market = listOf(DailyMarketData(listed.id, asOf, bd("110"))),
            fx = emptyList(),
        )
        val report = valuator.allocation(snapshot, asOf)
        assertTrue(!listed.locallyValued)
        assertMoney("1100", report.holdings.single { it.asset.id == listed.id }.valueEur, "mtm")
        // cash: 5000 - 1000 + 20 interest
        assertMoney("4020", report.cashEur, "cash")
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
        // fallback eurPerUsd=1 so 2*100 = 200
        assertEquals(0, bd("200").compareTo(stock.valueEur))
    }

    @Test
    fun latestFxIsCarriedBackwardWhenEarlierDaysHaveNoRow() {
        val eurPerUsd = bd("0.92")
        val snapshot = snapshot(
            assets = listOf(apple, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("5000")),
                buy("t1", apple.id, LocalDate.of(2026, 1, 2), bd("2"), bd("100"), Currency.USD, eurPerUsd, fees = BigDecimal.ZERO),
            ),
            market = listOf(DailyMarketData(apple.id, LocalDate.of(2026, 1, 2), bd("100"))),
            fx = listOf(CurrencyRate(asOf, eurPerUsd)),
        )
        val stock = valuator.valueHoldings(snapshot, LocalDate.of(2026, 1, 2)).single { it.asset.id == apple.id }
        assertMoney("184", stock.valueEur, "value")
    }

    @Test
    fun portfolioWithdrawalDoesNotReduceLocalInstrument() {
        val snapshot = snapshot(
            assets = listOf(ct, cash),
            transactions = listOf(
                cashIn("c0", LocalDate.of(2026, 1, 1), bd("5000")),
                buy("b1", ct.id, LocalDate.of(2026, 1, 2), bd("3000"), bd("1"), Currency.EUR, BigDecimal.ONE, fees = BigDecimal.ZERO),
                Transaction(
                    id = "w1",
                    assetId = cash.id,
                    date = LocalDate.of(2026, 6, 1),
                    type = TransactionType.WITHDRAWAL,
                    quantity = bd("100"),
                    unitPriceNative = BigDecimal.ONE,
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = BigDecimal.ONE,
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            market = emptyList(),
            fx = emptyList(),
        )
        val report = valuator.allocation(snapshot, asOf)
        assertMoney("3000", report.holdings.single { it.asset.id == ct.id }.valueEur, "ct")
        assertMoney("1900", report.cashEur, "cash")
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
        eurPerUsd: BigDecimal,
        fees: BigDecimal = bd("1.50"),
    ): Transaction {
        val eur = MoneyMath.toEur(native, currency, eurPerUsd)
        return Transaction(id, assetId, date, TransactionType.BUY, qty, native, eurPerUsd, eur, fees)
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
