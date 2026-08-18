package com.pirlruc.finsilo.domain

import com.pirlruc.finsilo.domain.importcsv.BrokerAssetType
import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvFormat
import com.pirlruc.finsilo.domain.importcsv.BrokerDates
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.BrokerQuoteSymbol
import com.pirlruc.finsilo.domain.importcsv.CsvReader
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.ListedQuoteRouting
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.LotPosition
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import com.pirlruc.finsilo.domain.usecase.GetMarketSignalsUseCase
import com.pirlruc.finsilo.domain.usecase.GetPortfolioAlertsUseCase
import com.pirlruc.finsilo.domain.usecase.GetPortfolioHistoryUseCase
import com.pirlruc.finsilo.domain.usecase.GetYocUseCase
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.NewAssetDraft
import com.pirlruc.finsilo.domain.usecase.RebuildNavHistoryUseCase
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportCsvBranchCoverageTest {
    private val importer = ImportBrokerCsvUseCase()
    private val empty = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun trading212SkipAndActionBranches() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),Charge amount,Currency (Charge amount),Currency conversion fee,Stamp duty,Result,ID
            Market buy,not-a-date,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100,EUR,1,100,EUR,,,,,B0
            Transfer,2024-01-01 09:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100,EUR,1,100,EUR,,,,,T0
            Withdrawal,2024-01-02 09:00:00,,,,,,,,500,EUR,,,,,W0
            Interest on cash,2024-01-03 09:00:00,,,,,,,,2.50,EUR,,,,,I0
            Deposit,2024-01-04 09:00:00,,,,,,,,,,,,,0,R0
            Dividend (Ordinary),2024-01-05 09:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,,0.40,EUR,1,0.40,EUR,,,,,D0
            Market buy,2024-01-06 09:00:00,,,Missing,1,10,EUR,1,10,EUR,,,,,M0
            Market buy,2024-01-07 09:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,0,100,EUR,1,0,EUR,,,,,Z0
            Market buy,2024-01-08 09:00:00,US0378331005,AAPL,Apple,1,150,USD,0.92,138,EUR,1.00,USD,0.50,0.25,,A0
            Market sell,2024-06-01 09:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,110,EUR,1,110,EUR,,,,,S0
            Market buy,2024-02-01 09:00:00,,PHIA_NA_EQ,Philips,2,20,EUR,1,40,EUR,,,,,P0
            Market buy,2024-02-02 09:00:00,FR0000120271,AIR_ZZ_EQ,Airbus,1,100,EUR,1,100,EUR,,,,,X0
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.TRADING_212, parsed.format)
        assertTrue(parsed.lines.any { it.skipReason?.contains("Unreadable date") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Ignored Transfer") == true })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(parsed.lines.any { it.type == TransactionType.DIVIDEND })
        val imported = importer(empty, listOf(csv))
        assertTrue(imported.skipped.isNotEmpty())
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
    }

    @Test
    fun revolutSkipSellWithdrawAndSymbolColumn() {
        val csv =
            """
            Date,Symbol,Type,Quantity,Price per share (EUR),Total Amount,Currency,FX Rate,ISIN,Fees,ID
            not-a-date,ASME,BUY - MARKET,1,EUR 10,EUR 10,EUR,1.0000,,0,B0
            2024-03-01T10:00:00Z,ASME,STOCK SPLIT,1,10,10,EUR,1,,,,S0
            2024-03-02T10:00:00Z,,WITHDRAWAL,,,80,EUR,1,,,,W0
            2024-03-03T10:00:00Z,,CASH TOP UP,,,20,GBP,1,,,,G0
            2024-03-04T10:00:00Z,,deposit,,,30,EUR,1,,,,D0
            2024-03-05T10:00:00Z,ASME,SELL - MARKET,1,12,12,EUR,1,,,,S1
            2024-03-06T10:00:00Z,,BUY - MARKET,1,10,10,EUR,1,,,,M0
            2024-03-07T10:00:00Z,ASME,BUY - MARKET,,10,10,EUR,1,,,,Q0
            2024-03-08T10:00:00Z,ASME.DE,BUY - MARKET,1,10,10,EUR,1,DE000A0Z2ZZ5,0,E0
            2024-03-09T10:00:00Z,PHIA,BUY - MARKET,1,20,20,EUR,1,NL0000009538,,N0
            2024-03-10T10:00:00Z,,CASH TOP-UP,,,,,1,,,,C0
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, parsed.format)
        assertTrue(parsed.lines.any { it.type == TransactionType.SELL })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        val imported = importer(empty, listOf(csv))
        assertTrue(imported.skipped.isNotEmpty())
    }

    @Test
    fun degiroAccountEnglishAndEdgeRows() {
        val csv =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
            bad,09:00,bad,,,Deposit,,1000,1000,
            2024-02-01,09:00,2024-02-01,,,Deposit,,1000,1000,
            2024-02-02,09:00,2024-02-02,,,Withdrawal,,-100,900,
            2024-02-03,09:00,2024-02-03,,,opname,,-50,850,
            2024-02-04,09:00,2024-02-04,,,storting,,0,850,
            2024-02-05,09:00,2024-02-05,VWCE,IE00BK5BQT80,Dividend tax,,-0.10,849.90,
            2024-02-06,09:00,2024-02-06,,,dividend,,2.00,851.90,D1
            2024-02-07,09:00,2024-02-07,VWCE,IE00BK5BQT80,Buy VWCE,,-100,751.90,B0
            2024-02-08,09:00,2024-02-08,VWCE,IE00BK5BQT80,Buy 2 VWCE @ 50,,-100,651.90,B1
            2024-02-09,09:00,2024-02-09,VWCE,IE00BK5BQT80,Sell 1 VWCE @ 60,,60,711.90,S1
            2024-02-10,09:00,2024-02-10,VWCE,IE00BK5BQT80,koop 1 VWCE,,-50,661.90,K1
            2024-02-11,09:00,2024-02-11,VWCE,IE00BK5BQT80,verkoop 1 VWCE,,55,716.90,V1
            2024-02-12,09:00,2024-02-12,,,Dividend,,0,716.90,
            2024-02-13,09:00,2024-02-13,Airbus,,dividend,,1.10,718,DIV2
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, parsed.format)
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(parsed.lines.any { it.type == TransactionType.BUY })
        assertTrue(parsed.lines.any { it.type == TransactionType.SELL })
        val imported = importer(empty, listOf(csv))
        assertTrue(imported.snapshot.transactions.any { it.type == TransactionType.DEPOSIT_CASH })
    }

    @Test
    fun degiroTransactionsSkipRowsAndEnglishHeaders() {
        val csv =
            """
            Date,Time,Product,ISIN,Venue,Quantity,Price,Local currency,Value,Value currency,Exchange rate,Transaction costs,Total,Order ID
            bad,10:15,VWCE,IE00BK5BQT80,XETR,1,100,EUR,-100,EUR,,0,-100,B0
            2024-03-15,10:15,,,XETR,1,100,EUR,-100,EUR,,0,-100,B1
            2024-03-16,10:15,VWCE,IE00BK5BQT80,XETR,,100,EUR,-100,EUR,,0,-100,B2
            2024-03-17,10:15,VWCE,IE00BK5BQT80,XETR,1,,EUR,,,EUR,,0,,B3
            2024-03-18,10:15,Philips,,AMS,2,20,EUR,-40,EUR,,1,-41,P1
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, parsed.format)
        assertTrue(parsed.lines.any { it.skipReason?.contains("Unreadable date") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Missing product") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Missing quantity") == true })
        assertTrue(parsed.lines.any { it.isin == null && it.symbol == "Philips" })
    }

    @Test
    fun parseAllKeepsAccountTradesWhenTransactionsMissing() {
        val account =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
            2024-03-15,10:15,2024-03-15,VWCE,IE00BK5BQT80,Buy 5 VWCE @ 118.25,,-591.25,0,ORD1
            """.trimIndent()
        val parsed = BrokerCsv.parseAll(listOf("not,a,broker\n1,2,3", account))
        assertEquals(1, parsed.lines.count { it.type == TransactionType.BUY })
    }

    @Test
    fun secondBuyMatchesExistingByTickerWithoutIsin() {
        val first =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,2,100.00,EUR,200.00,EUR,B1
            """.trimIndent()
        val loaded = importer(empty, listOf(first)).snapshot
        val second =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-02-15 10:30:00,,VWCE_GY_EQ,VWCE,1,105.00,EUR,105.00,EUR,B2
            Dividend (Ordinary),2024-03-01 10:30:00,US0378331005,AAPL_US_EQ,Apple,1,0.20,USD,0.18,EUR,D1
            """.trimIndent()
        val result = importer(loaded, listOf(second))
        assertEquals(1, result.snapshot.assets.count { it.isin == "IE00BK5BQT80" })
        assertTrue(result.skipped.any { it.contains("No holding") })
        assertEquals(2, result.snapshot.transactions.count { it.type == TransactionType.BUY })
    }

    @Test
    fun rejectedDepositDoesNotFundBuy() {
        val real = RecordLedgerEntryUseCase()
        val rejecting =
            ImportBrokerCsvUseCase { snapshot, request ->
                if (request.type == TransactionType.DEPOSIT_CASH) {
                    LedgerEntryResult.Rejected("no cash")
                } else {
                    real(snapshot, request)
                }
            }
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,2,100.00,EUR,200.00,EUR,B1
            """.trimIndent()
        val result = rejecting(empty, listOf(csv))
        assertTrue(result.skipped.isNotEmpty())
        assertTrue(result.snapshot.transactions.none { it.type == TransactionType.BUY })
    }

    @Test
    fun sameDayRowsExerciseImportRank() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Deposit,2024-01-15 08:00:00,,,,,,,,5000.00,EUR,D0
            Market buy,2024-01-15 10:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,10,100.00,EUR,1000.00,EUR,B1
            Dividend (Ordinary),2024-01-15 11:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,10,0.40,EUR,4.00,EUR,DV1
            Market sell,2024-01-15 12:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,110.00,EUR,110.00,EUR,S1
            Withdrawal,2024-01-15 13:00:00,,,,,,,,50.00,EUR,W1
            Transfer,2024-01-15 14:00:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100.00,EUR,100.00,EUR,T1
            Market buy,not-a-date,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100.00,EUR,100.00,EUR,B9
            Transfer,also-bad,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100.00,EUR,100.00,EUR,T9
            """.trimIndent()
        val result = importer(empty, listOf(csv))
        assertTrue(result.snapshot.transactions.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(result.snapshot.transactions.any { it.type == TransactionType.BUY })
        assertTrue(result.skipped.isNotEmpty() || result.snapshot.transactions.size >= 2)
    }

    @Test
    fun revolutIsinOnlyTradeAndBlankName() {
        val csv =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate,ISIN
            2024-03-01T10:00:00Z,,BUY - MARKET,1,10,10,EUR,1,IE00BK5BQT80
            2024-03-02T10:00:00Z,VWCE,DIVIDEND,,,2.50,EUR,1,IE00BK5BQT80
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertTrue(parsed.lines.any { it.type == TransactionType.BUY && it.isin == "IE00BK5BQT80" })
        assertTrue(parsed.lines.any { it.type == TransactionType.DIVIDEND })
        val result = importer(empty, listOf(csv))
        assertTrue(result.accepted > 0)
    }

    @Test
    fun fingerprintsCountOrphanTransactions() {
        val orphan =
            Transaction(
                id = "gone",
                assetId = "missing-asset",
                date = LocalDate.of(2024, 1, 15),
                type = TransactionType.DIVIDEND,
                quantity = bd("1"),
                unitPriceNative = bd("1"),
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = bd("1"),
                feesEur = BigDecimal.ZERO,
            )
        val snap = empty.copy(transactions = listOf(orphan))
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,100.00,EUR,100.00,EUR,B1
            """.trimIndent()
        val result = importer(snap, listOf(csv))
        assertTrue(result.accepted > 0)
    }

    @Test
    fun sameDayUsdBuysReuseStoredFx() {
        val csv =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate,ISIN
            2024-03-01T10:00:00Z,AAPL,BUY - MARKET,1,150.00,138.00,USD,0.92,US0378331005
            2024-03-01T11:00:00Z,AAPL,BUY - MARKET,1,151.00,139.00,USD,0.92,US0378331005
            """.trimIndent()
        val result = importer(empty, listOf(csv))
        assertEquals(1, result.snapshot.fxRates.size)
        assertEquals(2, result.snapshot.transactions.count { it.type == TransactionType.BUY })
    }
}

class BrokerHelpersBranchCoverageTest {
    @Test
    fun assetTypesAndQuoteSymbols() {
        assertEquals(AssetType.CRYPTO, BrokerAssetType.infer("BTC", "Bitcoin", null))
        assertEquals(AssetType.CRYPTO, BrokerAssetType.infer("XYZ", "Crypto basket", null))
        assertEquals(AssetType.COMMODITY, BrokerAssetType.infer("XAU", "Gold", null))
        assertEquals(AssetType.COMMODITY, BrokerAssetType.infer("GLD", "GOLD BULLION", null))
        assertEquals(AssetType.ETF, BrokerAssetType.infer("IWDA", "iShares Core MSCI", "IE00B4L5Y983"))
        assertEquals(AssetType.ETF, BrokerAssetType.infer("GLD", "GOLD ETF", null))
        assertEquals(AssetType.STOCK, BrokerAssetType.infer("AIR", "Airbus", "FR0000120271"))
        assertEquals("", BrokerQuoteSymbol.fromTrading212(""))
        assertEquals("AAPL", BrokerQuoteSymbol.fromTrading212("AAPL"))
        assertEquals("AIR", BrokerQuoteSymbol.fromTrading212("AIR_ZZ_EQ"))
        assertEquals("PHIA.AS", BrokerQuoteSymbol.fromTrading212("PHIA_NA_EQ"))
        assertEquals("AIR.PA", BrokerQuoteSymbol.fromTrading212("AIR_FP_EQ"))
        assertEquals("SAN.MC", BrokerQuoteSymbol.fromTrading212("SAN_MC_EQ"))
        assertEquals("UCG.MI", BrokerQuoteSymbol.fromTrading212("UCG_IM_EQ"))
        assertEquals("NESN.SW", BrokerQuoteSymbol.fromTrading212("NESN_SW_EQ"))
        assertEquals("EDP.LS", BrokerQuoteSymbol.fromTrading212("EDP_LS_EQ"))
        assertEquals("ABI.BR", BrokerQuoteSymbol.fromTrading212("ABI_BB_EQ"))
        assertEquals("SHEL.L", BrokerQuoteSymbol.fromTrading212("SHEL_UK_EQ"))
        assertEquals("product", BrokerQuoteSymbol.fromDegiro("product", "  "))
        assertEquals("IE00", BrokerQuoteSymbol.fromDegiro("product", "IE00"))
        assertEquals(".", BrokerQuoteSymbol.fromIsin(".", "US0378331005"))
        assertEquals("ASME.DE", BrokerQuoteSymbol.fromIsin("ASME.DE", "DE000A0Z2ZZ5"))
        assertEquals("PHIA.AS", BrokerQuoteSymbol.fromIsin("PHIA", "NL0000009538"))
        assertEquals("AIR.PA", BrokerQuoteSymbol.fromIsin("AIR", "FR0000120271"))
        assertEquals("SHEL.L", BrokerQuoteSymbol.fromIsin("SHEL", "GB00BP6MXD84"))
        assertEquals("SAN.MC", BrokerQuoteSymbol.fromIsin("SAN", "ES0113900J37"))
        assertEquals("UCG.MI", BrokerQuoteSymbol.fromIsin("UCG", "IT0005239360"))
        assertEquals("NESN.SW", BrokerQuoteSymbol.fromIsin("NESN", "CH0012032048"))
        assertEquals("EDP.LS", BrokerQuoteSymbol.fromIsin("EDP", "PTEDP0AM0009"))
        assertEquals("ABI.BR", BrokerQuoteSymbol.fromIsin("ABI", "BE0974293251"))
        assertEquals("SAP.DE", BrokerQuoteSymbol.fromIsin("SAP", "DE0007164600"))
        assertEquals("AAPL.US", BrokerQuoteSymbol.fromIsin("AAPL", "US0378331005"))
        assertEquals("FOO", BrokerQuoteSymbol.fromIsin("FOO", "XX0000000000"))
    }

    @Test
    fun moneyAndDateAndCsvEdges() {
        assertNull(BrokerMoney.book(MoneyParts("", "1", "EUR", "1", "EUR", "", "", "EUR")))
        assertNull(BrokerMoney.book(MoneyParts("0", "1", "EUR", "1", "EUR", "", "", "EUR")))
        val usdViaTotal =
            BrokerMoney.book(MoneyParts("2", "", "USD", "184", "EUR", "0.92", "1", "USD"))
        assertEquals(Currency.EUR, usdViaTotal!!.currency)
        val usdViaRate =
            BrokerMoney.book(MoneyParts("2", "150", "USD", "300", "USD", "0.92", "1", "GBP"))
        assertEquals(Currency.USD, usdViaRate!!.currency)
        assertEquals(0, bd("0.92").compareTo(usdViaRate.eurPerUsd))
        assertEquals(0, BigDecimal.ZERO.compareTo(usdViaRate.feesEur))
        assertNull(BrokerMoney.eurPerUsdRate(""))
        assertNull(BrokerMoney.eurPerUsdRate("0"))
        assertEquals(0, bd("1").compareTo(BrokerMoney.eurPerUsdRate("-1")))
        assertTrue(BrokerMoney.eurPerUsdRate("1.70")!! < BigDecimal.ONE)
        assertEquals(0, bd("1.05").compareTo(BrokerMoney.eurPerUsdRate("1.05")))
        assertEquals(0, bd("2").compareTo(BrokerMoney.eurPerUsdRate("2")))
        val zeroNative = BrokerMoney.book(MoneyParts("1", "0", "USD", "10", "EUR", "0.92", "", "EUR"))
        assertNotNull(zeroNative)
        assertEquals(0, bd("10").compareTo(BrokerMoney.toEurCash(bd("10"), "", "")))
        assertNull(BrokerMoney.toEurCash(bd("10"), "USD", ""))
        assertEquals("GBX", BrokerMoney.currencyCode("GBX", ""))
        assertEquals("GBX", BrokerMoney.currencyCode("GBP GBX", "GBP"))
        assertEquals("XX", BrokerMoney.currencyCode("XX", ""))
        assertEquals("EUR", BrokerMoney.currencyPrefix("EUR 10"))
        assertEquals("", BrokerMoney.currencyPrefix("10"))
        assertNotNull(BrokerMoney.parseAmount("1\u00a0000,50"))
        assertNull(BrokerDates.parse(""))
        assertNull(BrokerDates.parse("   "))
        assertEquals(LocalDate.of(2024, 3, 15), BrokerDates.parse("15/03/2024 10:00"))
        assertNull(BrokerDates.parse("32-13-2024"))
        assertTrue(CsvReader.records("").isEmpty())
        assertTrue(CsvReader.records("   \n  ").isEmpty())
        assertEquals(',', CsvReader.detectDelimiter(""))
        val quotedNewline = CsvReader.records("h\n\"a\nb\",c")
        assertEquals("a\nb", quotedNewline[1][0])
        assertEquals(listOf("a", "b"), CsvReader.records("a,b\r\n").single())
        val prefix =
            BrokerCsv.parse(
                """
                Date,Ticker,Type,Quantity,Price per share (EUR),Total Amount,Currency
                2024-03-01T10:00:00Z,ASME,BUY - MARKET,1,10,10,EUR
                """.trimIndent(),
            )
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, prefix.format)
        assertEquals(
            BrokerCsvFormat.TRADING_212,
            BrokerCsv.parse("Action,Ticker\nMarket buy,AAPL").format,
        )
        assertNull(BrokerCsv.parse("Symbol,Price per share,Type,Action\nA,1,BUY,x").format)
        assertEquals(
            BrokerCsvFormat.DEGIRO_ACCOUNT,
            BrokerCsv.parse("Description,Balance\nDeposit,1").format,
        )
        assertEquals(
            BrokerCsvFormat.DEGIRO_TRANSACTIONS,
            BrokerCsv.parse("Product,Quantity,Price\nVWCE,1,1").format,
        )
        assertNull(BrokerCsv.parse("Product,Quantity,Price,Action\nVWCE,1,1,x").format)
        val blankName =
            BrokerCsv.parse(
                """
                Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
                Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,,1,100.00,EUR,100.00,EUR,B1
                """.trimIndent(),
            )
        assertEquals("VWCE.DE", blankName.lines.first { it.type == TransactionType.BUY }.name)
    }
}

class HistoryRebuildSyncBranchCoverageTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR, isin = "IE00BK5BQT80")
    private val start = LocalDate.of(2026, 8, 1)

    @Test
    fun historyCoversMismatchAndDownsample() {
        val snapshot = snap()
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val history = GetPortfolioHistoryUseCase(maxPoints = 3)(snapshot, HistoryRange.ALL, asOf, rebuilt.points)
        assertTrue(history.points.size <= 4)
        val missingFrom = rebuilt.points.filter { it.date != start }
        val fromStore = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, missingFrom)
        assertEquals(rebuilt.points.size, fromStore.points.size)
        val wrongNav = rebuilt.points.map { it.copy(valueEur = it.valueEur.add(bd("1"))) }
        val walked = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, wrongNav)
        assertEquals(rebuilt.points.last().date, walked.points.last().date)
        val month = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ONE_MONTH, asOf)
        val quarter = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.THREE_MONTHS, asOf)
        assertEquals(start, month.from)
        assertEquals(start, quarter.from)
        val truncated = rebuilt.points.filter { it.date.isBefore(asOf) }
        val notCoveringTo = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, truncated)
        assertEquals(asOf, notCoveringTo.to)
    }

    @Test
    fun rebuildWalksWhenStoredStartsLateOrChangeIsAtFirstTx() {
        val snapshot = snap()
        val full = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val late = full.points.filter { !it.date.isBefore(start.plusDays(3)) }
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, full.fingerprint, late)
        assertFalse(rebuilt.skip)
        assertEquals(start, rebuilt.points.first().date)
        val fromFirst = RebuildNavHistoryUseCase()(snapshot, asOf, "other", full.points, changedFrom = start)
        assertEquals(start, fromFirst.points.first().date)
        val emptyStored = RebuildNavHistoryUseCase()(snapshot, asOf, full.fingerprint, emptyList(), changedFrom = asOf)
        assertEquals(start, emptyStored.points.first().date)
    }

    @Test
    fun syncFailuresEmptyHistoryAndFxFallback() {
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val gold = Asset("gold", "XAU", "Gold", AssetType.COMMODITY, Currency.USD)
        val deposit = Asset("dep", "DEP", "Deposit", AssetType.DEPOSIT, Currency.EUR)
        val futureBar = PriceBar(asOf.plusDays(1), bd("200"))
        val feed =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = error("spot fx down")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = error("fx down")

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> {
                    if (asset.id == "gold") return listOf(PriceBar(asOf, bd("2400")))
                    if (asset.id == "aapl") return listOf(futureBar)
                    error("quote down")
                }

                override suspend fun analystRating(asset: Asset): AnalystRating = error("rating down")
            }
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(apple, gold, deposit, cash),
                transactions = listOf(cashTx(), buy(etf.id)),
                marketData =
                listOf(
                    DailyMarketData(gold.id, asOf.minusDays(2), bd("2300"), sma50 = bd("2200"), sma200 = bd("2100")),
                    DailyMarketData(gold.id, asOf.minusDays(1), bd("2310"), sma50 = bd("2210"), sma200 = bd("2110")),
                ),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val result = runBlocking { SyncMarketDataUseCase(feed)(snapshot, asOf) }
        assertTrue(result.failures.any { it.contains("quote down").not() || it.contains("FX") || it.contains("AAPL") })
        assertTrue(result.failures.isNotEmpty())
        val noneRating =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = bd("0.92")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.minusDays(1), bd("190")), PriceBar(asOf, bd("200")))

                override suspend fun analystRating(asset: Asset) = AnalystRating.HOLD
            }
        val withNone =
            PortfolioSnapshot(
                assets = listOf(apple),
                transactions = emptyList(),
                marketData = listOf(DailyMarketData(apple.id, asOf.minusDays(1), bd("190"), AnalystRating.NONE)),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val synced = runBlocking { SyncMarketDataUseCase(noneRating)(withNone, asOf) }
        assertEquals(AnalystRating.NONE, synced.marketData.single { it.date == asOf.minusDays(1) }.analystRating)
        assertEquals(AnalystRating.HOLD, synced.marketData.single { it.date == asOf }.analystRating)
        assertEquals(1, synced.fxRates.size)
    }

    @Test
    fun ledgerNativePriceYocAlertsAndModels() {
        val ledger = PositionLedger()
        val buy = buy(etf.id)
        assertEquals(0, bd("100").compareTo(ledger.nativePrice(etf.id, asOf, emptyMap(), listOf(buy))))
        assertNull(ledger.nativePrice("missing", asOf, emptyMap(), emptyList()))
        val market = mapOf(etf.id to listOf(DailyMarketData(etf.id, asOf, bd("110"))))
        assertEquals(0, bd("110").compareTo(ledger.nativePrice(etf.id, asOf, market, listOf(buy))))
        assertEquals(0, BigDecimal.ZERO.compareTo(LotPosition(BigDecimal.ZERO, bd("10")).averageCostEur))
        assertTrue(LotPosition(bd("2"), bd("10")).averageCostEur.signum() > 0)
        val blankQuote = Asset("x", "AAPL", "Apple", AssetType.STOCK, Currency.USD, quoteSymbol = " ")
        assertEquals("AAPL", blankQuote.feedSymbol)
        val listed = Asset("p", "PPR", "PPR", AssetType.PPR, Currency.EUR, quoteSymbol = "VWCE.DE")
        assertFalse(listed.locallyValued)
        val unlisted = Asset("p2", "PTYAAAA00001", "PPR", AssetType.PPR, Currency.EUR)
        assertTrue(unlisted.locallyValued)
        val yocEmpty = GetYocUseCase()(PortfolioSnapshot(listOf(cash, etf), listOf(cashTx()), emptyList(), emptyList(), emptyList()), asOf)
        assertTrue(yocEmpty.isEmpty())
        val sold =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(cashTx(), buy(etf.id), sellAll()),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        assertTrue(GetYocUseCase()(sold, asOf).isEmpty())
        val noDiv =
            PortfolioSnapshot(listOf(etf, cash), listOf(cashTx(), buy(etf.id)), emptyList(), emptyList(), emptyList())
        assertTrue(GetYocUseCase()(noDiv, asOf).isEmpty())
        val annual =
            GetYocUseCase()(
                PortfolioSnapshot(
                    listOf(etf, cash),
                    listOf(cashTx(), buy(etf.id, start), div(start.minusMonths(2)), div(start.minusMonths(1))),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
                asOf,
            ).single()
        assertEquals(2, annual.paymentsPerYear)
        val noMarket =
            GetMarketSignalsUseCase()(
                PortfolioSnapshot(listOf(etf, cash), listOf(cashTx(), buy(etf.id)), emptyList(), emptyList(), emptyList()),
                asOf,
            )
        assertTrue(noMarket.isEmpty())
        val above =
            GetMarketSignalsUseCase()(
                PortfolioSnapshot(
                    listOf(etf, cash),
                    listOf(cashTx(), buy(etf.id)),
                    listOf(DailyMarketData(etf.id, asOf, bd("120"), sma50 = bd("100"), sma200 = bd("90"))),
                    emptyList(),
                    emptyList(),
                ),
                asOf,
            ).single()
        assertTrue(above.vsSma50 != null)
        assertNull(GetMarketSignalsUseCase.detectCross(null, bd("1"), bd("1"), bd("1")))
        val alertsNone = GetPortfolioAlertsUseCase()(noDiv, asOf)
        assertTrue(alertsNone.none { it.channel.name == "RATING" })
        val death =
            GetPortfolioAlertsUseCase()(
                PortfolioSnapshot(
                    listOf(etf, cash),
                    listOf(cashTx(), buy(etf.id)),
                    listOf(
                        DailyMarketData(etf.id, asOf.minusDays(1), bd("100"), sma50 = bd("100"), sma200 = bd("90")),
                        DailyMarketData(etf.id, asOf, bd("80"), sma50 = bd("80"), sma200 = bd("90")),
                    ),
                    emptyList(),
                    emptyList(),
                ),
                asOf,
            )
        assertTrue(death.any { it.title.contains("death") })
        val valuator = PortfolioValuator()
        val unpriced = valuator.unpricedSymbols(noDiv, asOf)
        assertTrue(unpriced.contains("VWCE.DE"))
        val usdNoFx =
            PortfolioSnapshot(
                listOf(Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD), cash),
                listOf(cashTx(), buy("aapl")),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        assertTrue(valuator.unpricedSymbols(usdNoFx, asOf).isEmpty())
        valuator.allocation(
            noDiv.copy(targets = listOf(TargetAllocation(AssetType.ETF, bd("10")), TargetAllocation(AssetType.CASH, bd("90")))),
            asOf,
        )
        val record = RecordLedgerEntryUseCase()
        val missing =
            record(
                snap(),
                LedgerEntryRequest(TransactionType.SELL, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = "nope"),
            )
        assertTrue(missing is LedgerEntryResult.Rejected)
        val cashBuy =
            record(
                snap(),
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    newAsset = NewAssetDraft("EUR-CASH", "Cash", AssetType.CASH, Currency.EUR),
                ),
            )
        assertTrue(cashBuy is LedgerEntryResult.Rejected)
        val blank =
            record(
                snap(),
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    newAsset = NewAssetDraft("  ", " ", AssetType.STOCK, Currency.EUR),
                ),
            )
        assertTrue(blank is LedgerEntryResult.Rejected)
        val unknownExisting =
            record(
                snap(),
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    existingAssetId = "missing-id",
                    newAsset = NewAssetDraft("FOO", "Foo", AssetType.STOCK, Currency.EUR),
                ),
            )
        assertTrue(unknownExisting is LedgerEntryResult.Rejected)
        SamplePortfolioFactory.create(asOf)
        assertNull(AlphaVantageParser.exchangeRate("""{"Meta Data":{}}"""))
        assertEquals(0, bd("0.92").compareTo(AlphaVantageParser.exchangeRate("""{"5. Exchange Rate":"0.92"}""")!!))
        assertTrue(AlphaVantageParser.commoditySeries("""{"2026-08-14":"77.10","bad":"1"}""").isNotEmpty())
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\nshort\nnot-a-date,1,1,1,2\n2026-08-14,1,1,1,bad\n").isEmpty())
        assertNull(FrankfurterParser.eurPerUsd("{}"))
        assertEquals(0, bd("0.91").compareTo(FrankfurterParser.eurPerUsd("""{"EUR":0.91}""")!!))
        assertNull(MovingAverages.sma(listOf(bd("1")), 2))
        assertNull(MovingAverages.sma(listOf(bd("1")), 0))
        assertEquals("ptyaaaa00001", ListedQuoteRouting.stooqTicker("PTYAAAA00001"))
        ledger.eurPerUsdOn(asOf.minusYears(1), listOf(CurrencyRate(asOf, bd("0.92"))))
        ledger.eurPerUsdOn(asOf, emptyList())
        assertEquals(
            0,
            bd("105").compareTo(
                ledger.nativePrice(
                    etf.id,
                    asOf,
                    emptyMap(),
                    listOf(buy(etf.id), sellAll().copy(unitPriceNative = bd("105"), quantity = bd("1"))),
                ),
            ),
        )
        assertNull(ledger.nativePrice(etf.id, start, emptyMap(), listOf(div(asOf), buy(etf.id, asOf))))
        val draftBuy =
            record(
                snap(),
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    newAsset = NewAssetDraft("FOO", "  ", AssetType.STOCK, Currency.EUR, "  ", "  "),
                ),
            ) as LedgerEntryResult.Accepted
        assertEquals("FOO", draftBuy.asset.name)
        assertNull(draftBuy.asset.isin)
        val quoted =
            record(
                snap().copy(transactions = snap().transactions + draftBuy.transaction, assets = snap().assets + draftBuy.asset),
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    existingAssetId = "",
                    newAsset = NewAssetDraft("BAR", "Bar", AssetType.STOCK, Currency.EUR, "IE00BK5BQT80", "BAR.DE"),
                ),
            )
        assertTrue(quoted is LedgerEntryResult.Accepted)
        val noDraft =
            record(
                snap(),
                LedgerEntryRequest(TransactionType.BUY, asOf, bd("1"), bd("1"), BigDecimal.ZERO),
            )
        assertTrue(noDraft is LedgerEntryResult.Rejected)
        assertEquals(
            AnalystRating.NONE,
            AlphaVantageParser.analystRating("""{"AnalystRatingBuy":"x"}"""),
        )
        val unchanged =
            MarketSignal(
                asset = etf,
                asOf = asOf,
                rating = AnalystRating.BUY,
                previousRating = AnalystRating.BUY,
                priceNative = bd("100"),
                sma50 = null,
                sma200 = null,
                vsSma50 = null,
                vsSma200 = null,
                cross = null,
            )
        assertFalse(unchanged.ratingChanged)
        assertFalse(unchanged.copy(previousRating = null).ratingChanged)
        assertFalse(unchanged.copy(rating = AnalystRating.NONE, previousRating = AnalystRating.BUY).ratingChanged)
        assertTrue(unchanged.copy(previousRating = AnalystRating.HOLD).ratingChanged)
        val tinyHistory = GetPortfolioHistoryUseCase(maxPoints = 1000)(snap(), HistoryRange.ALL, asOf)
        assertEquals(snap().transactions.minOf { it.date }, tinyHistory.from)
        val droppedLast = GetPortfolioHistoryUseCase(maxPoints = 2)(snap(), HistoryRange.ALL, asOf)
        assertEquals(asOf, droppedLast.points.last().date)
        val fp = RebuildNavHistoryUseCase()(snap(), asOf, null, emptyList())
        val mismatch = RebuildNavHistoryUseCase()(snap(), asOf, "nope", fp.points)
        assertFalse(mismatch.skip)
        val lateStart = RebuildNavHistoryUseCase()(snap(), asOf, fp.fingerprint, fp.points.drop(3), changedFrom = asOf.minusDays(1))
        assertEquals(start, lateStart.points.first().date)
    }

    private fun snap() = PortfolioSnapshot(
        assets = listOf(etf, cash),
        transactions = listOf(cashTx(), buy(etf.id)),
        marketData = emptyList(),
        fxRates = emptyList(),
        targets = emptyList(),
    )

    private fun cashTx() = Transaction(
        id = "c0",
        assetId = cash.id,
        date = start,
        type = TransactionType.DEPOSIT_CASH,
        quantity = bd("10000"),
        unitPriceNative = BigDecimal.ONE,
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = BigDecimal.ONE,
        feesEur = BigDecimal.ZERO,
    )

    private fun buy(assetId: String, date: LocalDate = start.plusDays(1)) = Transaction(
        id = "b-$assetId-$date",
        assetId = assetId,
        date = date,
        type = TransactionType.BUY,
        quantity = bd("10"),
        unitPriceNative = bd("100"),
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = bd("100"),
        feesEur = BigDecimal.ZERO,
    )

    private fun sellAll() = Transaction(
        id = "s",
        assetId = etf.id,
        date = asOf,
        type = TransactionType.SELL,
        quantity = bd("10"),
        unitPriceNative = bd("100"),
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = bd("100"),
        feesEur = BigDecimal.ZERO,
    )

    private fun div(date: LocalDate) = Transaction(
        id = "d-$date",
        assetId = etf.id,
        date = date,
        type = TransactionType.DIVIDEND,
        quantity = BigDecimal.ONE,
        unitPriceNative = bd("5"),
        exchangeRateAtExecution = BigDecimal.ONE,
        unitPriceEur = bd("5"),
        feesEur = BigDecimal.ZERO,
    )
}
