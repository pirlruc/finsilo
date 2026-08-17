package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerBuySellAllTypesTest {
    private val useCase = RecordLedgerEntryUseCase(newId = { "tx-new" })
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR-CASH", "Cash", AssetType.CASH, Currency.EUR)
    private val valuator = PortfolioValuator()
    private val ledger = PositionLedger()

    @Test
    fun buyAndSellAreAcceptedForEveryInvestmentType() {
        AssetType.entries.filter { it != AssetType.CASH }.forEach { type ->
            val asset = sample(type)
            val funded = funded(asset)
            val buyExisting =
                useCase(
                    funded,
                    LedgerEntryRequest(
                        type = TransactionType.BUY,
                        date = asOf,
                        quantity = bd("1"),
                        unitPriceNative = bd("10"),
                        feesEur = BigDecimal.ZERO,
                        existingAssetId = asset.id,
                        eurPerUsd = bd("0.92"),
                    ),
                )
            assertTrue(buyExisting is LedgerEntryResult.Accepted, "$type buy: $buyExisting")

            val afterBuy = persist(funded, buyExisting as LedgerEntryResult.Accepted)
            val sell =
                useCase(
                    afterBuy,
                    LedgerEntryRequest(
                        type = TransactionType.SELL,
                        date = asOf,
                        quantity = bd("1"),
                        unitPriceNative = bd("11"),
                        feesEur = BigDecimal.ZERO,
                        existingAssetId = asset.id,
                        eurPerUsd = bd("0.92"),
                    ),
                )
            assertTrue(sell is LedgerEntryResult.Accepted, "$type sell: $sell")
        }
    }

    @Test
    fun eurCryptoBuyIsAcceptedAndUsdQuoteConvertsWithFx() {
        val btc = Asset("btc", "BTC", "Bitcoin", AssetType.CRYPTO, Currency.EUR)
        val snapshot = cashOnly(bd("20000")).copy(fxRates = emptyList())
        val result =
            useCase(
                snapshot,
                LedgerEntryRequest(
                    type = TransactionType.BUY,
                    date = asOf,
                    quantity = bd("0.5"),
                    unitPriceNative = bd("10000"),
                    feesEur = BigDecimal.ZERO,
                    newAsset = NewAssetDraft("BTC", "Bitcoin", AssetType.CRYPTO, Currency.EUR),
                    eurPerUsd = bd("0.50"),
                ),
            )
        val accepted = result as LedgerEntryResult.Accepted
        assertEquals(Currency.EUR, accepted.asset.baseCurrency)
        assertEquals(0, bd("10000").compareTo(accepted.transaction.unitPriceEur))
        assertEquals(0, bd("0.50").compareTo(requireNotNull(accepted.fxRate).eurPerUsd))

        val held =
            persist(snapshot, accepted).copy(
                marketData = listOf(DailyMarketData(accepted.asset.id, asOf, bd("20000"))),
            )
        val holding = valuator.allocation(held, asOf).holdings.single { it.asset.id == accepted.asset.id }
        // 0.5 * 20000 USD * 0.50 EUR/USD = 5000 EUR
        assertEquals(0, bd("5000").compareTo(holding.valueEur))
    }

    @Test
    fun localInstrumentSellRedeemsNav() {
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val bought =
            persist(
                cashOnly(bd("5000")),
                useCase(
                    cashOnly(bd("5000")),
                    LedgerEntryRequest(
                        type = TransactionType.BUY,
                        date = LocalDate.of(2026, 1, 2),
                        quantity = bd("3000"),
                        unitPriceNative = BigDecimal.ONE,
                        feesEur = BigDecimal.ZERO,
                        newAsset = NewAssetDraft(ct.symbol, ct.name, AssetType.CT, Currency.EUR),
                    ),
                ) as LedgerEntryResult.Accepted,
            )
        val sold =
            persist(
                bought,
                useCase(
                    bought,
                    LedgerEntryRequest(
                        type = TransactionType.SELL,
                        date = asOf,
                        quantity = bd("1000"),
                        unitPriceNative = BigDecimal.ONE,
                        feesEur = BigDecimal.ZERO,
                        existingAssetId = bought.assets.single { it.assetType == AssetType.CT }.id,
                    ),
                ) as LedgerEntryResult.Accepted,
            )
        val ctId = sold.assets.single { it.assetType == AssetType.CT }.id
        assertEquals(0, bd("2000").compareTo(valuator.allocation(sold, asOf).holdings.single { it.asset.id == ctId }.valueEur))
        assertEquals(0, bd("3000").compareTo(ledger.cashEur(sold.transactions, sold.assets.associateBy { it.id })))
    }

    @Test
    fun cashCannotBeBoughtOrSold() {
        val snapshot = cashOnly(bd("100"))
        val buy =
            useCase(
                snapshot,
                LedgerEntryRequest(
                    type = TransactionType.BUY,
                    date = asOf,
                    quantity = bd("1"),
                    unitPriceNative = bd("1"),
                    feesEur = BigDecimal.ZERO,
                    existingAssetId = cash.id,
                ),
            )
        assertTrue((buy as LedgerEntryResult.Rejected).reason.contains("Deposit"))
        val sell =
            useCase(
                snapshot,
                LedgerEntryRequest(
                    type = TransactionType.SELL,
                    date = asOf,
                    quantity = bd("1"),
                    unitPriceNative = bd("1"),
                    feesEur = BigDecimal.ZERO,
                    existingAssetId = cash.id,
                ),
            )
        assertTrue((sell as LedgerEntryResult.Rejected).reason.contains("Withdrawal"))
    }

    private fun sample(type: AssetType): Asset {
        val currency = if (type == AssetType.CRYPTO || type == AssetType.COMMODITY) Currency.USD else Currency.EUR
        val symbol = if (type == AssetType.ETF) "VWCE.DE" else type.name
        val quote = null
        return Asset(type.name.lowercase(), symbol, type.name, type, currency, quoteSymbol = quote)
    }

    private fun funded(asset: Asset): PortfolioSnapshot {
        val buy =
            Transaction(
                id = "b0",
                assetId = asset.id,
                date = LocalDate.of(2026, 1, 2),
                type = TransactionType.BUY,
                quantity = bd("10"),
                unitPriceNative = bd("10"),
                exchangeRateAtExecution = if (asset.baseCurrency == Currency.USD) bd("0.92") else BigDecimal.ONE,
                unitPriceEur = if (asset.baseCurrency == Currency.USD) bd("9.2") else bd("10"),
                feesEur = BigDecimal.ZERO,
            )
        return PortfolioSnapshot(
            assets = listOf(cash, asset),
            transactions = listOf(cashIn(bd("5000")), buy),
            marketData = emptyList(),
            fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
            targets = emptyList(),
        )
    }

    private fun persist(snapshot: PortfolioSnapshot, accepted: LedgerEntryResult.Accepted): PortfolioSnapshot {
        val assets = if (accepted.createdAsset) snapshot.assets + accepted.asset else snapshot.assets
        val fx = accepted.fxRate?.let { snapshot.fxRates + it } ?: snapshot.fxRates
        return snapshot.copy(assets = assets, transactions = snapshot.transactions + accepted.transaction, fxRates = fx)
    }

    private fun cashOnly(amount: BigDecimal) = PortfolioSnapshot(
        assets = listOf(cash),
        transactions = listOf(cashIn(amount)),
        marketData = emptyList(),
        fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
        targets = emptyList(),
    )

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
