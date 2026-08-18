package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvDetect
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvFormat
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.BrokerQuoteSymbol
import com.pirlruc.finsilo.domain.importcsv.CsvReader
import com.pirlruc.finsilo.domain.importcsv.ImportFingerprints
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParserBranchCoverageTest {
    private val asOf = LocalDate.of(2026, 8, 16)

    @Test
    fun trading212TradeRowSkipsBlankTickerAndZeroQty() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,,VWCE,1,100.00,EUR,100.00,EUR,ISINONLY
            Market buy,2024-01-15 10:30:00,,,VWCE,,,EUR,,,EUR,B0
            Market buy,2024-01-16 10:30:00,IE00BK5BQT80,VWCE,VWCE,0,100,EUR,0,EUR,B1
            Interest,2024-01-17 10:30:00,,,,,,,1.00,EUR,INT1
            Withdraw,2024-01-18 10:30:00,,,,,,,50.00,EUR,W1
            Market sell,2024-01-19 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,120,EUR,120,EUR,S1
            Deposit,not-a-date,,,,,,,10,EUR,BAD
            Limit order,2024-01-20 10:30:00,IE00BK5BQT80,VWCE,VWCE,1,100,EUR,100,EUR,IGN
            Deposit,2024-01-21 10:30:00,,,,,,,0,EUR,Z
            Deposit,2024-01-22 10:30:00,,,,,,,10,GBP,FX
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.TRADING_212, parsed.format)
        assertTrue(parsed.lines.any { it.skipReason?.contains("ticker") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("quantity/price") == true })
        assertTrue(parsed.lines.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(parsed.lines.any { it.type == TransactionType.SELL })
        assertTrue(parsed.lines.any { it.skipReason?.contains("date") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Ignored") == true })
    }

    @Test
    fun degiroAccountTradesDividendsAndCashEdges() {
        val csv =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
            2024-02-14,09:00,2024-02-14,VWCE,IE00BK5BQT80,Buy 2 VWCE @ 50,,-100,1,B1
            2024-02-15,09:00,2024-02-15,VWCE,IE00BK5BQT80,Sell 1 VWCE @ 55,,55,56,S1
            2024-02-16,09:00,2024-02-16,VWCE,IE00BK5BQT80,Buy VWCE @ 50,,-50,6,B2
            2024-02-17,09:00,2024-02-17,VWCE,IE00BK5BQT80,Buy 0 VWCE @ 50,,0,6,B0
            2024-02-18,09:00,2024-02-18,,,Deposit,,0,6,
            2024-02-19,09:00,2024-02-19,,,Withdrawal,,-10,0,W1
            2024-02-20,09:00,2024-02-20,,,Dividend,,2,2,
            2024-02-21,09:00,2024-02-21,VWCE,IE00BK5BQT80,Dividend,,0,2,D0
            2024-02-22,09:00,2024-02-22,,,Opname,,-1,1,
            2024-02-23,09:00,2024-02-23,,,Storting,,25,26,
            not-a-date,09:00,x,,,Deposit,,1,1,
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, parsed.format)
        assertTrue(parsed.lines.any { it.type == TransactionType.BUY })
        assertTrue(parsed.lines.any { it.type == TransactionType.SELL })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Transactions.csv") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("missing amount") == true || it.skipReason?.contains("Cash amount") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Dividend missing") == true })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(parsed.lines.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(parsed.lines.any { it.skipReason?.contains("date") == true })
    }

    @Test
    fun revolutTradeRowSkipsAndCashEdges() {
        val csv =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate,ISIN
            2026-02-17T10:12:51.768Z,,BUY - MARKET,1,100,100,EUR,1.0000,US0378331005
            2026-02-17T10:12:51.768Z,,BUY - MARKET,,,10,EUR,1.0000,
            2026-02-18T10:12:51.768Z,ASME,BUY - MARKET,,,10,EUR,1.0000,
            2026-02-19T10:12:51.768Z,ASME,SELL - MARKET,1,100,100,EUR,1.0000,
            2026-02-20T10:12:51.768Z,,CASH TOP-UP,,,0,EUR,1.0000,
            2026-02-21T10:12:51.768Z,,CASH TOP-UP,,,10,GBP,1.0000,
            2026-02-22T10:12:51.768Z,,WITHDRAW,,,20,EUR,1.0000,
            2026-02-23T10:12:51.768Z,ASME,SPINOFF,,,1,EUR,1.0000,
            not-a-date,ASME,BUY - MARKET,1,100,100,EUR,1.0000,
            2026-02-24T10:12:51.768Z,ASME,DIVIDEND,,,2,USD,0.92,US0378331005
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, parsed.format)
        assertTrue(parsed.lines.any { it.skipReason?.contains("ticker") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("quantity/price") == true })
        assertTrue(parsed.lines.any { it.type == TransactionType.SELL })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(parsed.lines.any { it.skipReason?.contains("Ignored") == true })
        assertTrue(parsed.lines.any { it.skipReason?.contains("date") == true })
        assertTrue(parsed.lines.any { it.type == TransactionType.DIVIDEND })
    }

    @Test
    fun brokerDetectHitsEachHeaderAlias() {
        assertEquals(BrokerCsvFormat.TRADING_212, BrokerCsvDetect.from(listOf("Action", "No. of shares", "Time")))
        assertEquals(BrokerCsvFormat.TRADING_212, BrokerCsvDetect.from(listOf("Action", "Ticker", "Time")))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(listOf("Symbol", "Price per share", "Type")))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(listOf("Ticker", "Price per share USD", "Type")))
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(listOf("Omschrijving", "Mutatie")))
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(listOf("Description", "Change")))
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(listOf("Description", "Saldo")))
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(listOf("Description", "Balance")))
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(listOf("Aantal", "Koers", "Product")))
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(listOf("Quantity", "Price", "Product")))
        assertNull(BrokerCsvDetect.from(listOf("Action")))
        assertNull(BrokerCsvDetect.from(listOf("Ticker", "Type")))
        assertNull(BrokerCsvDetect.from(listOf("Action", "Quantity", "Price", "Product")))
        assertEquals(
            "",
            ImportFingerprints.of(
                BrokerCsvLine(
                    date = null,
                    type = TransactionType.BUY,
                    skipReason = null,
                    symbol = "X",
                    name = "X",
                    isin = null,
                    quoteSymbol = null,
                    assetType = AssetType.STOCK,
                    quantity = bd("1"),
                    unitPriceNative = bd("1"),
                    currency = Currency.EUR,
                    feesEur = BigDecimal.ZERO,
                    eurPerUsd = null,
                    externalId = null,
                    sourceLine = 2,
                    format = BrokerCsvFormat.TRADING_212,
                ),
            ),
        )
        assertEquals(
            "",
            ImportFingerprints.of(
                BrokerCsvLine(
                    date = LocalDate.of(2024, 1, 15),
                    type = null,
                    skipReason = null,
                    symbol = "X",
                    name = "X",
                    isin = null,
                    quoteSymbol = null,
                    assetType = AssetType.STOCK,
                    quantity = bd("1"),
                    unitPriceNative = bd("1"),
                    currency = Currency.EUR,
                    feesEur = BigDecimal.ZERO,
                    eurPerUsd = null,
                    externalId = null,
                    sourceLine = 2,
                    format = BrokerCsvFormat.TRADING_212,
                ),
            ),
        )
        assertEquals("", BrokerQuoteSymbol.fromTrading212("  "))
        assertEquals("PTYAAAA00001", BrokerQuoteSymbol.fromIsin("PTYAAAA00001", null))
        assertEquals("FOO", BrokerQuoteSymbol.fromIsin("FOO", "ZZ0000000000"))
        assertEquals("FOO.DE", BrokerQuoteSymbol.fromIsin("FOO", "DE0000000000"))
        assertEquals(1, GetYocUseCase.inferPaymentsPerYear(1))
        assertEquals(2, GetYocUseCase.inferPaymentsPerYear(2))
        assertEquals(4, GetYocUseCase.inferPaymentsPerYear(3))
        assertEquals(4, GetYocUseCase.inferPaymentsPerYear(5))
        assertEquals(12, GetYocUseCase.inferPaymentsPerYear(6))
        assertNull(GetYocUseCase.inferPaymentsPerYear(0))
        assertNull(GetYocUseCase.inferPaymentsPerYear(-1))
        val headerAbove =
            BrokerCsv.parse(
                """
                Portfolio export
                Action,Time,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
                Market buy,2024-01-15 10:30:00,VWCE,VWCE,1,100,EUR,100,EUR,B1
                """.trimIndent(),
            )
        assertEquals(BrokerCsvFormat.TRADING_212, headerAbove.format)
        assertEquals(',', CsvReader.detectDelimiter("a,b\nc,d"))
        assertEquals(';', CsvReader.detectDelimiter("a;b\nc;d"))
        assertEquals(',', CsvReader.detectDelimiter("   "))
        assertTrue(CsvReader.records("").isEmpty())
        assertEquals("VWCE", BrokerQuoteSymbol.fromTrading212("VWCE"))
        assertEquals("VWCE.DE", BrokerQuoteSymbol.fromTrading212("VWCE_GY_EQ"))
        assertEquals("FOO", BrokerQuoteSymbol.fromTrading212("FOO_ZZ_EQ"))
        assertEquals("P", BrokerQuoteSymbol.fromDegiro("P", "  "))
        assertEquals(".", BrokerQuoteSymbol.fromIsin(".", "US0378331005"))
        assertNull(BrokerMoney.book(MoneyParts("0", "10", "EUR", "10", "EUR", "", "", "EUR")))
        assertNull(BrokerMoney.book(MoneyParts("1", "", "GBP", "10", "GBP", "", "", "GBP")))
        assertNotNull(BrokerMoney.book(MoneyParts("1", "10", "USD", "9.2", "EUR", "", "1", "USD")))
        assertNotNull(BrokerMoney.toEurCash(bd("10"), "USD", "0.92"))
        assertNull(BrokerMoney.toEurCash(bd("10"), "USD", "0"))
        assertNull(BrokerMoney.eurPerUsdRate("0"))
        assertNotNull(BrokerMoney.eurPerUsdRate("1.20"))
        assertEquals("EUR", BrokerMoney.currencyCode("", "eur"))
        assertEquals("", BrokerMoney.currencyPrefix("10.0"))
        assertEquals("USD", BrokerMoney.currencyPrefix("USD10"))
    }

    @Test
    fun marketParsersHitMissAndInvalidNumericBranches() {
        assertNull(FrankfurterParser.eurPerUsd("{}"))
        assertTrue(FrankfurterParser.eurPerUsdSeries("{}").isEmpty())
        assertEquals(0, bd("0.92").compareTo(FrankfurterParser.eurPerUsd("""{"EUR":0.92}""")!!))
        assertTrue(FrankfurterParser.eurPerUsdSeries("""{"2026-08-01":{"EUR":0.91}}""").isNotEmpty())
        assertNull(AlphaVantageParser.exchangeRate("""{"Meta Data":{}}"""))
        assertEquals(0, bd("1.08").compareTo(AlphaVantageParser.exchangeRate("""{"5. Exchange Rate":"1.08"}""")!!))
        assertEquals(AnalystRating.NONE, AlphaVantageParser.analystRating("""{"Symbol":"AAPL"}"""))
        assertEquals(AnalystRating.NONE, AlphaVantageParser.analystRating("""{"AnalystRatingBuy":"x"}"""))
        assertEquals(AnalystRating.BUY, AlphaVantageParser.analystRating("""{"AnalystRatingBuy":"10"}"""))
        assertEquals(AnalystRating.STRONG_BUY, AlphaVantageParser.analystRating("""{"AnalystRatingStrongBuy":"10"}"""))
        assertEquals(AnalystRating.HOLD, AlphaVantageParser.analystRating("""{"AnalystRatingHold":"10"}"""))
        assertEquals(AnalystRating.SELL, AlphaVantageParser.analystRating("""{"AnalystRatingSell":"10"}"""))
        assertEquals(AnalystRating.STRONG_SELL, AlphaVantageParser.analystRating("""{"AnalystRatingStrongSell":"10"}"""))
        assertTrue(AlphaVantageParser.commoditySeries("""{"data":[{"date":"not-a-date","value":"1.0"}]}""").isEmpty())
        assertTrue(AlphaVantageParser.commoditySeries("""{"data":[{"date":"2026-08-01","value":"70.5"}]}""").isNotEmpty())
        assertTrue(AlphaVantageParser.commoditySeries("""{"data":{"2026-08-01":"71.0"}}""").isNotEmpty())
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\n2026-08-01").isEmpty())
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\nbad,1,2,3,4").isEmpty())
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\n2026-08-01,1,2,3,x").isEmpty())
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\n2026-08-01,1,2,3,10").isNotEmpty())
        assertTrue(CoinGeckoParser.dailyCloses("{}").isEmpty())
        assertTrue(CoinGeckoParser.dailyCloses("""{"prices":[[1719907200000,1.5]]}""").isNotEmpty())
        val sample = SamplePortfolioFactory.create(asOf)
        assertTrue(sample.transactions.isNotEmpty())
        assertTrue(sample.fxRates.isNotEmpty())
    }

    @Test
    fun syncRatingOnBarUsesNoneWhenStoredMissingOrBlank() {
        val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
        val feed =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = bd("0.92")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = listOf(CurrencyRate(to, bd("0.92")))

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.minusDays(1), bd("100")), PriceBar(asOf, bd("101")))

                override suspend fun analystRating(asset: Asset) = AnalystRating.BUY
            }
        val emptyStored = PortfolioSnapshot(listOf(etf), emptyList(), emptyList(), emptyList(), emptyList())
        val synced = runBlocking { SyncMarketDataUseCase(feed)(emptyStored, asOf) }
        assertEquals(AnalystRating.NONE, synced.marketData.single { it.date == asOf.minusDays(1) }.analystRating)
        val noneStored =
            emptyStored.copy(marketData = listOf(DailyMarketData(etf.id, asOf.minusDays(1), bd("100"), AnalystRating.NONE)))
        val withNone = runBlocking { SyncMarketDataUseCase(feed)(noneStored, asOf) }
        assertEquals(AnalystRating.NONE, withNone.marketData.single { it.date == asOf.minusDays(1) }.analystRating)
        val futureOnly = runBlocking {
            SyncMarketDataUseCase(
                object : MarketFeed {
                    override suspend fun eurPerUsd() = bd("0.92")

                    override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()

                    override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.plusDays(3), bd("1")))

                    override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
                },
            )(emptyStored, asOf)
        }
        assertTrue(futureOnly.marketData.isEmpty())
    }
}
