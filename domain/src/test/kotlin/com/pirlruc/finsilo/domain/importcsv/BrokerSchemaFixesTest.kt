package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.usecase.GetTimeWeightedReturnUseCase
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.NewAssetDraft
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerSchemaFixesTest {
    @Test
    fun portugueseDegiroTransactionsAreNotAnAccountStatement() {
        val headers = listOf(
            "Data",
            "Hora",
            "Produto",
            "ISIN",
            "Bolsa",
            "Quantidade",
            "Preços",
            "",
            "Valor local",
            "",
            "Valor EUR",
            "Taxa de Câmbio",
            "Total EUR",
        )
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(headers))
        assertTrue("mudanca" in BrokerHeaders.CHANGE)
        assertTrue("t" in BrokerHeaders.FX)
    }

    @Test
    fun degiroTransactionsUseEurValueFxAndSkipZeroPricePairs() {
        val csv =
            """
            Data,Hora,Produto,ISIN,Bolsa,Quantidade,Preços,,Valor local,,Valor EUR,Taxa de Câmbio,Taxa Autofx,Custos de transação,Total EUR
            03-03-2024,10:00,Widget,US0000000001,NDQ,2,"10,00",USD,"-20,00",USD,"-10,00","1,2500","0,20","0,05","-10,25"
            03-03-2024,11:00,Widget,US0000000001,NDQ,10,"0,00",USD,"0,00",USD,"0,00",,,,"0,00"
            03-03-2024,11:05,Widget,US0000000001,NDQ,-10,"0,00",USD,"0,00",USD,"0,00",,,,"0,00"
            04-03-2024,11:00,Widget,US0000000001,NDQ,-1,"0,00",USD,"0,00",USD,"0,00",,,,"0,00"
            04-03-2024,12:00,Widget,US0000000001,NDQ,-2,"5,00",USD,"-10,00",USD,"-9,00","1,1111",,,"-9,00"
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, parsed.format)
        val buy = parsed.lines.single { it.type == TransactionType.BUY }
        assertEquals(0, bd("2").compareTo(buy.quantity))
        assertEquals(0, bd("10").compareTo(buy.unitPriceNative))
        assertEquals(Currency.USD, buy.currency)
        assertEquals(0, bd("0.5").compareTo(buy.eurPerUsd))
        assertEquals(0, bd("0.25").compareTo(buy.feesEur))
        assertTrue(parsed.lines.any { it.skipReason == "Corporate action" })
        assertTrue(parsed.lines.any { it.skipReason == "Zero price" })
        assertEquals(1, parsed.lines.count { it.type == TransactionType.SELL })
        assertNull(parsed.lines.filter { it.skipReason == "Zero price" || it.skipReason == "Corporate action" }.find { it.type == TransactionType.SELL })
    }

    @Test
    fun degiroAccountKeepsNegativeDividendsAndSkipsFxSweeps() {
        val csv =
            """
            Data,Hora,Data Valor,Produto,ISIN,Descrição,T.,Mudança,,Saldo,,ID da Ordem
            02-01-2024,09:00,02-01-2024,Acme,IE00TEST0001,Dividendo,,EUR,"-1,25",EUR,"10,00",
            02-01-2024,09:01,02-01-2024,,,Levantamento de divisa,"1,1000",USD,"-10,00",USD,"0,00",
            02-01-2024,09:02,02-01-2024,,,Degiro Cash Sweep Transfer,,EUR,"-5,00",EUR,"100,00",
            02-01-2024,09:03,02-01-2024,Acme,IE00TEST0001,Compra 1 Acme,,EUR,,EUR,"100,00",
            02-01-2024,09:04,02-01-2024,,,Depósito,,EUR,"50,00",EUR,"150,00",
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, parsed.format)
        val dividend = parsed.lines.single { it.type == TransactionType.DIVIDEND }
        assertTrue(dividend.unitPriceNative.signum() < 0)
        assertEquals(0, bd("-1.25").compareTo(dividend.unitPriceNative))
        assertTrue(parsed.lines.any { it.skipReason?.contains("Levantamento de divisa") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Cash Sweep") == true })
        assertTrue(parsed.lines.none { it.skipReason?.contains("Levantamento de divisa") == true && it.type != null })
        assertTrue(parsed.lines.none { it.type == TransactionType.BUY })
        assertTrue(parsed.lines.any { it.skipReason?.contains("missing amount") == true })
        val asset = Asset("a1", "ACME", "Acme", AssetType.STOCK, Currency.EUR, isin = "IE00TEST0001")
        val imported = ImportBrokerCsvUseCase()(
            PortfolioSnapshot(listOf(asset), emptyList(), emptyList(), emptyList(), emptyList()),
            listOf(csv),
        )
        val booked = imported.snapshot.transactions.single { it.type == TransactionType.DIVIDEND }
        assertTrue(booked.unitPriceEur.signum() < 0)
        assertTrue(imported.snapshot.transactions.none { it.type == TransactionType.WITHDRAWAL })
        val described =
            """
            Data,Hora,Data Valor,Produto,ISIN,Descrição,T.,Mudança,,Saldo,,ID da Ordem
            01-02-2024,09:00,01-02-2024,,,"Depósito na conta: 1.234,56 EUR",,,EUR,"2.000,00",
            """.trimIndent()
        val fromText = BrokerCsv.parse(described).lines.single { it.type == TransactionType.DEPOSIT_CASH }
        assertEquals(0, bd("1234.56").compareTo(fromText.quantity))
    }

    @Test
    fun revolutCommodityAndCryptoDoNotMatchDegiro() {
        val commodityHeaders = listOf(
            "Type",
            "Product",
            "Started Date",
            "Completed Date",
            "Description",
            "Amount",
            "Fee",
            "Currency",
            "State",
            "Balance",
        )
        val cryptoHeaders = listOf("Symbol", "Type", "Quantity", "Price", "Value", "Fees", "Date")
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(commodityHeaders))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(cryptoHeaders))
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(listOf("Descrição", "Saldo")))
        val commodity =
            """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Exchanged to XAU,0.10000,0.001,XAU,COMPLETED,0.10000
            Exchange,Current,2024-05-02 10:00:00,2024-05-02 10:00:01,Exchanged to XPD,0.20000,0.002,XPD,COMPLETED,0.20000
            """.trimIndent()
        val metals = BrokerCsv.parse(commodity)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, metals.format)
        assertTrue(metals.lines.all { it.skipReason == "Commodity buy has no EUR amount" })
        assertTrue(metals.lines.none { it.unitPriceNative.signum() > 0 && it.type == TransactionType.BUY })
        val euro = "\u00e2\u0082\u00ac"
        val gap = "\u00e2\u0080\u00af"
        val crypto =
            """
            Symbol,Type,Quantity,Price,Value,Fees,Date
            BONK,Buy,2,"${euro}1,234.50","${euro}2,469.00",${euro}0.10,"Jul 12, 2025, 9:15:30${gap}AM"
            ETH,Staking reward,0.5,${euro}100.00,${euro}50.00,${euro}0.00,"Jul 13, 2025, 1:00:00 AM"
            SOL,Learn reward,3,${euro}10.00,${euro}30.00,${euro}0.00,"Jul 14, 2025, 8:00:00 AM"
            DOT,Stake,1,${euro}5.00,${euro}5.00,${euro}0.00,"Jul 15, 2025, 9:00:00 AM"
            XRP,Unstake,1,${euro}1.00,${euro}1.00,${euro}0.00,"Jul 16, 2025, 9:00:00 AM"
            BTC,Sell,0.1,${euro}200.00,${euro}20.00,${euro}0.05,"Jul 17, 2025, 9:00:00 AM"
            """.trimIndent()
        val coins = BrokerCsv.parse(crypto)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, coins.format)
        val bonk = coins.lines.single { it.symbol == "BONK" }
        assertEquals(LocalDate.of(2025, 7, 12), bonk.date)
        assertEquals(AssetType.CRYPTO, bonk.assetType)
        assertEquals(TransactionType.BUY, bonk.type)
        assertEquals(0, bd("1234.50").compareTo(bonk.unitPriceNative))
        assertEquals(0, bd("0.10").compareTo(bonk.feesEur))
        val reward = coins.lines.single { it.symbol == "ETH" }
        assertEquals(TransactionType.BUY, reward.type)
        assertEquals(0, BigDecimal.ZERO.compareTo(reward.unitPriceNative))
        assertEquals(TransactionType.BUY, coins.lines.single { it.symbol == "SOL" }.type)
        assertEquals(0, BigDecimal.ZERO.compareTo(coins.lines.single { it.symbol == "SOL" }.unitPriceNative))
        assertTrue(coins.lines.any { it.skipReason?.contains("Stake") == true })
        assertTrue(coins.lines.any { it.skipReason?.contains("Unstake") == true })
        assertEquals(TransactionType.SELL, coins.lines.single { it.symbol == "BTC" }.type)
        assertEquals(AssetType.CRYPTO, coins.lines.single { it.symbol == "ZKJ".let { "BTC" } }.assetType)
        assertEquals(AssetType.CRYPTO, BrokerAssetType.infer("ZKJ", "ZKJ", null))
        assertEquals(AssetType.CRYPTO, BrokerAssetType.infer("CRO", "CRO", null))
    }

    @Test
    fun trading212UsdBuyDoesNotDoubleCountConversionFee() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),Currency conversion fee,Charge amount
            Market buy,2024-06-01 10:00:00,IE00TEST0001,VWCE,World,2,100,USD,2.50,180,EUR,1.50,0.25
            Market buy,2024-06-02 10:00:00,DK0000000001,NOVO,Novo,1,50,EUR,1,50,EUR,0,0
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        val usd = parsed.lines.single { it.symbol == "VWCE.DE" }
        assertEquals(0, bd("0.9").compareTo(usd.eurPerUsd))
        assertEquals(0, bd("0.25").compareTo(usd.feesEur))
        assertEquals("NOVO.CO", parsed.lines.single { it.tickerOrSymbol() == "NOVO.CO" }.symbol)
        assertEquals(Currency.EUR, parsed.lines.single { it.symbol == "NOVO.CO" }.currency)
    }

    @Test
    fun coinGeckoClosesIgnoreTotalVolumes() {
        val json =
            """
            {"prices":[[1755302400000,111.5]],"total_volumes":[[1755302400000,999999.0]]}
            """.trimIndent()
        val closes = CoinGeckoParser.dailyCloses(json)
        assertEquals(1, closes.size)
        assertEquals(0, bd("111.5").compareTo(closes.single().closeNative))
    }

    @Test
    fun autoFundedBuyAcceptsWhenRawMultiplyDiffersByDust() {
        val qty = BigDecimal("1.2345678901234567")
        val price = BigDecimal("9.8765432109876543")
        val raw = qty.multiply(price)
        val narrowed = qty.multiply(price, MathContext(16, java.math.RoundingMode.HALF_EVEN))
        assertTrue(raw.compareTo(narrowed) != 0)
        val ids = ArrayDeque(listOf("asset-1", "tx-deposit", "tx-buy"))
        val result = RecordLedgerEntryUseCase(newId = { ids.removeFirst() })(
            PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            LedgerEntryRequest(
                type = TransactionType.BUY,
                date = LocalDate.of(2024, 1, 2),
                quantity = qty,
                unitPriceNative = price,
                feesEur = BigDecimal.ZERO,
                newAsset = NewAssetDraft("VWCE.DE", "World", AssetType.ETF, Currency.EUR),
            ),
            fundBuyWithDeposit = true,
        )
        val accepted = result as LedgerEntryResult.Accepted
        val cost = MoneyMath.round(accepted.transaction.notionalEur.add(accepted.transaction.feesEur))
        assertEquals(0, cost.compareTo(accepted.fundingDeposit!!.notionalEur))
    }

    @Test
    fun storedEndOfDayNavIsNotThePreCashflowNav() {
        val day1 = LocalDate.of(2024, 4, 1)
        val day2 = LocalDate.of(2024, 4, 2)
        val cash = Asset("cash", "EUR-CASH", "Euro cash", AssetType.CASH, Currency.EUR)
        val snapshot = PortfolioSnapshot(
            assets = listOf(cash),
            transactions = listOf(
                cashMove("dep", cash.id, day1, TransactionType.DEPOSIT_CASH, bd("1000")),
                cashMove("wd", cash.id, day2, TransactionType.WITHDRAWAL, bd("400")),
            ),
            marketData = emptyList(),
            fxRates = emptyList(),
            targets = emptyList(),
        )
        val stored = listOf(NavPoint(day1, bd("1000")), NavPoint(day2, bd("600")))
        val report = GetTimeWeightedReturnUseCase()(snapshot, day2, stored)
        assertEquals(0, bd("0").compareTo(report.twrPercent))
    }

    private fun cashMove(
        id: String,
        assetId: String,
        date: LocalDate,
        type: TransactionType,
        amount: BigDecimal,
    ): Transaction = Transaction(
        id = id,
        assetId = assetId,
        date = date,
        type = type,
        quantity = amount,
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
    )

    private fun BrokerCsvLine.tickerOrSymbol(): String = symbol
}
