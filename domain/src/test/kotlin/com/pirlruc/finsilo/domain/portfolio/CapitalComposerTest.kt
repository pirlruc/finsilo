package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapitalComposerTest {
    private val cash = Asset("asset-cash", "EUR-CASH", "Euro cash", AssetType.CASH, Currency.EUR)
    private val vwce = Asset("vwce", "VWCE.DE", "All-World", AssetType.ETF, Currency.EUR)
    private val ppr = Asset("ppr", "PPR1", "Unlisted PPR", AssetType.PPR, Currency.EUR)
    private val iwda = Asset("iwda", "IWDA.AS", "World", AssetType.ETF, Currency.EUR)
    private val asOf = LocalDate.of(2026, 2, 1)

    @Test
    fun splitsDepositsFromCashInterestAndBroker() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("1000"), BrokerSource.TRADING_212),
                    cashTx("i1", TransactionType.INTEREST, bd("2.5"), BrokerSource.TRADING_212),
                    cashTx("d2", TransactionType.DEPOSIT_CASH, bd("300"), BrokerSource.DEGIRO),
                    cashTx("w1", TransactionType.WITHDRAWAL, bd("50"), BrokerSource.DEGIRO),
                    cashTx("manual", TransactionType.DEPOSIT_CASH, bd("10"), source = null),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val totals = CapitalComposer.compose(snapshot, emptyList(), bd("1262.5"))
        assertEquals(0, bd("1260").compareTo(totals.contributedEur))
        assertEquals(0, bd("2.5").compareTo(totals.cashInterestEur))
        assertEquals(0, bd("2.5").compareTo(totals.totalGainEur))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("1000").compareTo(t212.contributedEur))
        assertEquals(0, bd("2.5").compareTo(t212.cashInterestEur))
        assertEquals(0, bd("1002.5").compareTo(t212.cashEur))
        assertEquals(0, bd("2.5").compareTo(t212.gainEur))
        val degiro = totals.brokers.single { it.source == BrokerSource.DEGIRO }
        assertEquals(0, bd("250").compareTo(degiro.contributedEur))
        assertEquals(0, bd("0").compareTo(degiro.cashInterestEur))
        assertEquals(0, bd("250").compareTo(degiro.cashEur))
        assertEquals(0, bd("0").compareTo(degiro.gainEur))
    }

    @Test
    fun brokerGainUsesOpenLotsAndUninvestedCash() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, vwce, ppr, iwda),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("1000"), BrokerSource.TRADING_212),
                    trade("b1", vwce.id, TransactionType.BUY, bd("2"), bd("100"), BrokerSource.TRADING_212),
                    trade("b2", ppr.id, TransactionType.BUY, bd("1"), bd("50"), BrokerSource.REVOLUT),
                    cashTx("d2", TransactionType.DEPOSIT_CASH, bd("50"), BrokerSource.REVOLUT),
                    trade("i2", ppr.id, TransactionType.INTEREST, bd("5"), bd("1"), BrokerSource.REVOLUT),
                    cashTx("d3", TransactionType.DEPOSIT_CASH, bd("100"), BrokerSource.DEGIRO),
                    trade("b3", iwda.id, TransactionType.BUY, bd("1"), bd("100"), BrokerSource.DEGIRO),
                    trade("s-only", vwce.id, TransactionType.SELL, bd("1"), bd("100"), BrokerSource.DEGIRO),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val holding =
            HoldingValuation(
                asset = vwce,
                quantity = bd("2"),
                priceEur = null,
                valueEur = bd("220"),
                costEur = bd("200"),
                unrealizedPnlEur = bd("20"),
            )
        val unused =
            HoldingValuation(
                asset = Asset("x", "X", "X", AssetType.STOCK, Currency.EUR),
                quantity = BigDecimal.ZERO,
                priceEur = bd("10"),
                valueEur = BigDecimal.ZERO,
                costEur = BigDecimal.ZERO,
                unrealizedPnlEur = BigDecimal.ZERO,
            )
        val emptyIwda =
            HoldingValuation(
                asset = iwda,
                quantity = BigDecimal.ZERO,
                priceEur = null,
                valueEur = BigDecimal.ZERO,
                costEur = BigDecimal.ZERO,
                unrealizedPnlEur = BigDecimal.ZERO,
            )
        val totals = CapitalComposer.compose(snapshot, listOf(holding, unused, emptyIwda), bd("800"))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("1000").compareTo(t212.contributedEur))
        assertEquals(0, bd("800").compareTo(t212.cashEur))
        assertEquals(0, bd("20").compareTo(t212.gainEur))
        val revolut = totals.brokers.single { it.source == BrokerSource.REVOLUT }
        assertEquals(0, bd("50").compareTo(revolut.contributedEur))
        assertEquals(0, bd("5").compareTo(revolut.gainEur))
        val degiro = totals.brokers.single { it.source == BrokerSource.DEGIRO }
        assertEquals(0, bd("100").compareTo(degiro.contributedEur))
        assertEquals(0, bd("0").compareTo(degiro.gainEur))
    }

    @Test
    fun laterBrokerSpendsCashDepositedAtAnotherBroker() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, vwce),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("1000"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 10)),
                    trade("b1", vwce.id, TransactionType.BUY, bd("2"), bd("100"), BrokerSource.DEGIRO, LocalDate.of(2024, 1, 15)),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val holding =
            HoldingValuation(
                asset = vwce,
                quantity = bd("2"),
                priceEur = bd("110"),
                valueEur = bd("220"),
                costEur = bd("200"),
                unrealizedPnlEur = bd("20"),
            )
        val totals = CapitalComposer.compose(snapshot, listOf(holding), bd("800"))
        assertEquals(0, bd("20").compareTo(totals.totalGainEur))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("1000").compareTo(t212.contributedEur))
        assertEquals(0, bd("800").compareTo(t212.cashEur))
        assertEquals(0, bd("-200").compareTo(t212.gainEur))
        val degiro = totals.brokers.single { it.source == BrokerSource.DEGIRO }
        assertEquals(0, bd("0").compareTo(degiro.contributedEur))
        assertEquals(0, bd("0").compareTo(degiro.cashEur))
        assertEquals(0, bd("220").compareTo(degiro.gainEur))
    }

    @Test
    fun leftoverLotPartialFillLocalSellAndMarketableDividend() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, vwce, ppr),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("1000"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 1)),
                    trade("b1", vwce.id, TransactionType.BUY, bd("2"), bd("100"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 10)),
                    trade("s1", vwce.id, TransactionType.SELL, bd("1"), bd("110"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 20)),
                    trade("div", vwce.id, TransactionType.DIVIDEND, bd("5"), bd("1"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 25)),
                    cashTx("d2", TransactionType.DEPOSIT_CASH, bd("100"), BrokerSource.REVOLUT, LocalDate.of(2024, 2, 1)),
                    trade("b2", ppr.id, TransactionType.BUY, bd("1"), bd("50"), BrokerSource.REVOLUT, LocalDate.of(2024, 2, 2)),
                    trade("s2", ppr.id, TransactionType.SELL, bd("1"), bd("50"), BrokerSource.REVOLUT, LocalDate.of(2024, 2, 3)),
                    cashTx("i-manual", TransactionType.INTEREST, bd("1"), source = null, date = LocalDate.of(2024, 2, 4)),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val holding =
            HoldingValuation(
                asset = vwce,
                quantity = bd("1"),
                priceEur = bd("110"),
                valueEur = bd("110"),
                costEur = bd("100"),
                unrealizedPnlEur = bd("10"),
            )
        val totals = CapitalComposer.compose(snapshot, listOf(holding), bd("1016"))
        assertEquals(0, bd("26").compareTo(totals.totalGainEur))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("1000").compareTo(t212.contributedEur))
        assertEquals(0, bd("915").compareTo(t212.cashEur))
        assertEquals(0, bd("25").compareTo(t212.gainEur))
        val revolut = totals.brokers.single { it.source == BrokerSource.REVOLUT }
        assertEquals(0, bd("100").compareTo(revolut.contributedEur))
        assertEquals(0, bd("100").compareTo(revolut.cashEur))
        assertEquals(0, bd("0").compareTo(revolut.gainEur))
    }

    @Test
    fun preferredPursePartialThenOtherBrokersAndUntaggedLots() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, vwce, iwda),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("50"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 1)),
                    cashTx("d2", TransactionType.DEPOSIT_CASH, bd("150"), BrokerSource.DEGIRO, LocalDate.of(2024, 1, 1)),
                    trade("b1", vwce.id, TransactionType.BUY, bd("1"), bd("180"), BrokerSource.DEGIRO, LocalDate.of(2024, 1, 2)),
                    trade("b2", iwda.id, TransactionType.BUY, bd("1"), bd("10"), source = null, date = LocalDate.of(2024, 1, 3)),
                    trade("ghost", "missing", TransactionType.BUY, bd("1"), bd("5"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 4)),
                    trade("cash-buy", cash.id, TransactionType.BUY, bd("1"), bd("1"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 5)),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val vwceHolding =
            HoldingValuation(
                asset = vwce,
                quantity = bd("1"),
                priceEur = bd("180"),
                valueEur = bd("180"),
                costEur = bd("180"),
                unrealizedPnlEur = BigDecimal.ZERO,
            )
        val totals = CapitalComposer.compose(snapshot, listOf(vwceHolding), bd("4"))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("50").compareTo(t212.contributedEur))
        assertEquals(0, bd("4").compareTo(t212.cashEur))
        val degiro = totals.brokers.single { it.source == BrokerSource.DEGIRO }
        assertEquals(0, bd("150").compareTo(degiro.contributedEur))
        assertEquals(0, bd("0").compareTo(degiro.cashEur))
        assertEquals(0, bd("30").compareTo(degiro.gainEur))
        assertTrue(totals.brokers.none { it.source == BrokerSource.REVOLUT })
    }

    @Test
    fun fullLotConsumeSecondInterestAndMissingAsset() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, vwce, iwda),
                transactions =
                listOf(
                    cashTx("d1", TransactionType.DEPOSIT_CASH, bd("300"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 1)),
                    cashTx("i1", TransactionType.INTEREST, bd("1"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 2)),
                    cashTx("i2", TransactionType.INTEREST, bd("1"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 3)),
                    trade("b1", vwce.id, TransactionType.BUY, bd("1"), bd("100"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 4)),
                    trade("s1", vwce.id, TransactionType.SELL, bd("1"), bd("110"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 5)),
                    trade("b2", vwce.id, TransactionType.BUY, bd("1"), bd("80"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 6)),
                    trade("b3", iwda.id, TransactionType.BUY, bd("1"), bd("50"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 7)),
                    trade("s-miss", "missing", TransactionType.SELL, bd("1"), bd("10"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 8)),
                    trade("i-miss", "missing", TransactionType.INTEREST, bd("1"), bd("1"), BrokerSource.TRADING_212, LocalDate.of(2024, 1, 9)),
                ),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val vwceHolding =
            HoldingValuation(
                asset = vwce,
                quantity = bd("1"),
                priceEur = bd("80"),
                valueEur = bd("80"),
                costEur = bd("80"),
                unrealizedPnlEur = BigDecimal.ZERO,
            )
        val totals = CapitalComposer.compose(snapshot, listOf(vwceHolding), bd("183"))
        val t212 = totals.brokers.single { it.source == BrokerSource.TRADING_212 }
        assertEquals(0, bd("300").compareTo(t212.contributedEur))
        assertEquals(0, bd("2").compareTo(t212.cashInterestEur))
        assertEquals(0, bd("183").compareTo(t212.cashEur))
        assertEquals(0, bd("13").compareTo(t212.gainEur))
    }

    private fun cashTx(
        id: String,
        type: TransactionType,
        amount: BigDecimal,
        source: BrokerSource?,
        date: LocalDate = asOf,
    ) = Transaction(
        id = id,
        assetId = cash.id,
        date = date,
        type = type,
        quantity = amount,
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
        source = source,
    )

    private fun trade(
        id: String,
        assetId: String,
        type: TransactionType,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        source: BrokerSource?,
        date: LocalDate = asOf,
    ) = Transaction(
        id = id,
        assetId = assetId,
        date = date,
        type = type,
        quantity = quantity,
        unitPriceNative = unitPrice,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = unitPrice,
        feesEur = BigDecimal.ZERO,
        source = source,
    )
}
