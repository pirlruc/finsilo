package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
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

class RecordLedgerEntryUseCaseTest {

    private val useCase = RecordLedgerEntryUseCase(newId = { "tx-new" })
    private val asOf = LocalDate.of(2026, 8, 16)
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
    private val ct = Asset("ct", "CT", "Certificados", AssetType.CT, Currency.EUR)
    private val cash = Asset("cash", "EUR-CASH", "Cash", AssetType.CASH, Currency.EUR)

    @Test
    fun sellAboveRemainingFifoQuantityIsRejected() {
        val snapshot = fundedApple(qty = bd("10"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.SELL,
                date = asOf,
                quantity = bd("11"),
                unitPriceNative = bd("200"),
                feesEur = BigDecimal.ZERO,
                existingAssetId = apple.id,
                eurPerUsd = bd("0.92"),
            ),
        )
        assertTrue(result is LedgerEntryResult.Rejected)
        assertTrue((result as LedgerEntryResult.Rejected).reason.contains("exceeds remaining"))
    }

    @Test
    fun sellOfRemainingFifoQuantityIsAccepted() {
        val snapshot = fundedApple(qty = bd("10"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.SELL,
                date = asOf,
                quantity = bd("10"),
                unitPriceNative = bd("200"),
                feesEur = BigDecimal.ZERO,
                existingAssetId = apple.id,
                eurPerUsd = bd("0.92"),
            ),
        )
        val accepted = result as LedgerEntryResult.Accepted
        assertEquals(TransactionType.SELL, accepted.transaction.type)
        assertEquals(0, bd("184").compareTo(accepted.transaction.unitPriceEur))
    }

    @Test
    fun withdrawalAboveCashIsRejected() {
        val snapshot = fundedApple(qty = bd("10"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.WITHDRAWAL,
                date = asOf,
                quantity = bd("2000"),
                unitPriceNative = BigDecimal.ONE,
                feesEur = BigDecimal.ZERO,
            ),
        )
        assertTrue(result is LedgerEntryResult.Rejected)
        assertTrue((result as LedgerEntryResult.Rejected).reason.contains("exceeds uninvested cash"))
    }

    @Test
    fun withdrawalOfRemainingCashIsAccepted() {
        val snapshot = cashOnly(bd("250"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.WITHDRAWAL,
                date = asOf,
                quantity = bd("250"),
                unitPriceNative = BigDecimal.ONE,
                feesEur = BigDecimal.ZERO,
            ),
        )
        assertTrue(result is LedgerEntryResult.Accepted)
    }

    @Test
    fun usdBuyStoresNativeTimesEurPerUsd() {
        val snapshot = cashOnly(bd("5000"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.BUY,
                date = asOf,
                quantity = bd("2"),
                unitPriceNative = bd("100"),
                feesEur = bd("1.50"),
                newAsset = NewAssetDraft("AAPL", "Apple Inc.", AssetType.STOCK, Currency.USD, isin = "US0378331005"),
                eurPerUsd = bd("0.50"),
            ),
        )
        val accepted = result as LedgerEntryResult.Accepted
        assertEquals(0, bd("50").compareTo(accepted.transaction.unitPriceEur))
        assertEquals("US0378331005", accepted.asset.isin)
        assertTrue(accepted.createdAsset)
        assertEquals(null, accepted.fxRate)
    }

    @Test
    fun usdBuySeedsMtMFxOnlyWhenThatDateHasNoRow() {
        val emptyFx = cashOnly(bd("5000")).copy(fxRates = emptyList())
        val seeded = useCase(
            emptyFx,
            LedgerEntryRequest(
                type = TransactionType.BUY,
                date = asOf,
                quantity = bd("2"),
                unitPriceNative = bd("100"),
                feesEur = BigDecimal.ZERO,
                newAsset = NewAssetDraft("AAPL", "Apple Inc.", AssetType.STOCK, Currency.USD),
                eurPerUsd = bd("0.50"),
            ),
        ) as LedgerEntryResult.Accepted
        val seededFx = requireNotNull(seeded.fxRate)
        assertEquals(0, bd("0.50").compareTo(seededFx.eurPerUsd))
        assertEquals(asOf, seededFx.date)

        val alreadyQuoted = cashOnly(bd("5000"))
        val skipped = useCase(
            alreadyQuoted,
            LedgerEntryRequest(
                type = TransactionType.BUY,
                date = asOf,
                quantity = bd("1"),
                unitPriceNative = bd("100"),
                feesEur = BigDecimal.ZERO,
                newAsset = NewAssetDraft("MSFT", "Microsoft", AssetType.STOCK, Currency.USD),
                eurPerUsd = bd("0.10"),
            ),
        ) as LedgerEntryResult.Accepted
        assertEquals(null, skipped.fxRate)
        assertEquals(0, bd("10").compareTo(skipped.transaction.unitPriceEur))
    }

    @Test
    fun pprKeepsIsinAndQuoteSymbolSeparateFromDisplayName() {
        val snapshot = cashOnly(bd("5000"))
        val result = useCase(
            snapshot,
            LedgerEntryRequest(
                type = TransactionType.BUY,
                date = asOf,
                quantity = bd("10"),
                unitPriceNative = bd("12.50"),
                feesEur = BigDecimal.ZERO,
                newAsset = NewAssetDraft(
                    symbol = "PPR Moderado",
                    name = "PPR Moderado",
                    assetType = AssetType.PPR,
                    baseCurrency = Currency.EUR,
                    isin = "PTYAAAA00001",
                    quoteSymbol = "VWCE.DE",
                ),
            ),
        )
        val accepted = result as LedgerEntryResult.Accepted
        assertEquals("PTYAAAA00001", accepted.asset.isin)
        assertEquals("VWCE.DE", accepted.asset.quoteSymbol)
        assertEquals("VWCE.DE", accepted.asset.feedSymbol)
        assertEquals("PPR Moderado", accepted.asset.symbol)
    }

    @Test
    fun interestOnCtIsAcceptedAndInterestOnStockIsRejected() {
        val withCt = cashOnly(bd("4000")).copy(
            assets = listOf(cash, ct),
            transactions = listOf(
                cashIn(bd("4000")),
                Transaction(
                    id = "b-ct",
                    assetId = ct.id,
                    date = LocalDate.of(2026, 1, 2),
                    type = TransactionType.BUY,
                    quantity = bd("3000"),
                    unitPriceNative = BigDecimal.ONE,
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = BigDecimal.ONE,
                    feesEur = BigDecimal.ZERO,
                ),
            ),
        )
        val ok = useCase(
            withCt,
            LedgerEntryRequest(
                type = TransactionType.INTEREST,
                date = asOf,
                quantity = BigDecimal.ONE,
                unitPriceNative = bd("28.50"),
                feesEur = BigDecimal.ZERO,
                existingAssetId = ct.id,
            ),
        )
        assertTrue(ok is LedgerEntryResult.Accepted)

        val stockSnap = fundedApple(bd("5"))
        val bad = useCase(
            stockSnap,
            LedgerEntryRequest(
                type = TransactionType.INTEREST,
                date = asOf,
                quantity = BigDecimal.ONE,
                unitPriceNative = bd("1"),
                feesEur = BigDecimal.ZERO,
                existingAssetId = apple.id,
            ),
        )
        assertTrue(bad is LedgerEntryResult.Rejected)
    }

    private fun cashOnly(amount: BigDecimal) = PortfolioSnapshot(
        assets = listOf(cash),
        transactions = listOf(cashIn(amount)),
        marketData = emptyList(),
        fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
        targets = emptyList(),
    )

    private fun fundedApple(qty: BigDecimal): PortfolioSnapshot {
        val buyCost = qty.multiply(bd("100")).multiply(bd("0.92"))
        val deposit = buyCost.add(bd("100"))
        return PortfolioSnapshot(
            assets = listOf(cash, apple),
            transactions = listOf(
                cashIn(deposit),
                Transaction(
                    id = "b1",
                    assetId = apple.id,
                    date = LocalDate.of(2026, 1, 2),
                    type = TransactionType.BUY,
                    quantity = qty,
                    unitPriceNative = bd("100"),
                    exchangeRateAtExecution = bd("0.92"),
                    unitPriceEur = bd("92"),
                    feesEur = BigDecimal.ZERO,
                ),
            ),
            marketData = emptyList(),
            fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
            targets = emptyList(),
        )
    }

    private fun cashIn(amount: BigDecimal) = Transaction(
        id = "c0",
        assetId = cash.id,
        date = LocalDate.of(2026, 1, 1),
        type = TransactionType.DEPOSIT_CASH,
        quantity = amount,
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
    )
}

class SaveTargetAllocationUseCaseTest {

    private val useCase = SaveTargetAllocationUseCase()

    @Test
    fun weightsMustSumToOneHundred() {
        val rejected = useCase(
            mapOf(
                AssetType.ETF to bd("40"),
                AssetType.STOCK to bd("20"),
            ),
        )
        assertTrue(rejected is TargetAllocationResult.Rejected)

        val accepted = useCase(
            mapOf(
                AssetType.ETF to bd("40"),
                AssetType.STOCK to bd("20"),
                AssetType.CRYPTO to bd("10"),
                AssetType.PPR to bd("15"),
                AssetType.CT to bd("6"),
                AssetType.DEPOSIT to bd("4"),
                AssetType.COMMODITY to bd("3"),
                AssetType.CASH to bd("2"),
            ),
        ) as TargetAllocationResult.Accepted
        assertEquals(8, accepted.targets.size)
        assertEquals(0, bd("40").compareTo(accepted.targets.single { it.assetType == AssetType.ETF }.weightPercent))
    }

    @Test
    fun negativeWeightIsRejected() {
        val result = useCase(mapOf(AssetType.ETF to bd("-1"), AssetType.CASH to bd("101")))
        assertTrue(result is TargetAllocationResult.Rejected)
    }
}
