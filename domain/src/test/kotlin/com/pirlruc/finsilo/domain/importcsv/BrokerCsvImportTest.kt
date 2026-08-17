package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerCsvParserTest {
    @Test
    fun detectsTrading212AndBooksUsdBuyWithEurTotal() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),Charge amount,Currency (Charge amount),ID
            Market buy,2024-01-15 10:30:00,US0378331005,AAPL_US_EQ,Apple,10,150.00,USD,0.92,1380.00,EUR,1.50,EUR,BUY1
            Deposit,2024-01-10 09:00:00,,,,,,,,1000.00,EUR,,,DEP1
            Dividend (Ordinary),2024-04-01 12:00:00,US0378331005,AAPL_US_EQ,Apple,10,0.25,USD,0.90,2.25,EUR,,,DIV1
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.TRADING_212, parsed.format)
        val buy = parsed.lines.single { it.type == TransactionType.BUY }
        assertEquals("AAPL.US", buy.symbol)
        assertEquals(Currency.USD, buy.currency)
        assertEquals(0, bd("10").compareTo(buy.quantity))
        assertEquals(0, bd("150").compareTo(buy.unitPriceNative))
        assertTrue(buy.eurPerUsd != null)
        assertTrue(parsed.lines.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(parsed.lines.any { it.type == TransactionType.DIVIDEND })
    }

    @Test
    fun detectsDegiroTransactionsDutchSemicolon() {
        val csv =
            """
            Datum;Tijd;Product;ISIN;Venue;Aantal;Koers;Lokale valuta;Waarde;Valuta;Wisselkoers;Transactiekosten;Totaal;Order ID
            15-03-2024;10:15;VANGUARD FTSE ALL-WORLD;IE00BK5BQT80;XETR;5;118,25;EUR;-591,25;EUR;;-1,00;-592,25;ORD1
            20-06-2024;11:00;VANGUARD FTSE ALL-WORLD;IE00BK5BQT80;XETR;-2;125,00;EUR;250;EUR;;0;250;ORD2
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, parsed.format)
        val buy = parsed.lines.first { it.type == TransactionType.BUY }
        assertEquals(AssetType.ETF, buy.assetType)
        assertEquals("IE00BK5BQT80.DE", buy.quoteSymbol)
        assertEquals(0, bd("5").compareTo(buy.quantity))
        assertEquals(TransactionType.SELL, parsed.lines.first { it.type == TransactionType.SELL }.type)
    }

    @Test
    fun detectsDegiroAccountDividendsAndDeposits() {
        val csv =
            """
            Datum,Tijd,Valutadatum,Product,ISIN,Omschrijving,FX,Mutatie,Saldo,Order Id
            01-02-2024,09:00,01-02-2024,,,Storting,,1000,1000,
            15-04-2024,08:00,15-04-2024,VANGUARD FTSE ALL-WORLD,IE00BK5BQT80,Dividend,,2.50,1002.50,D1
            16-04-2024,08:00,16-04-2024,VANGUARD FTSE ALL-WORLD,IE00BK5BQT80,Dividendbelasting,,-0.40,1002.10,
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, parsed.format)
        assertEquals(TransactionType.DEPOSIT_CASH, parsed.lines.first { it.type == TransactionType.DEPOSIT_CASH }.type)
        assertEquals(TransactionType.DIVIDEND, parsed.lines.first { it.type == TransactionType.DIVIDEND }.type)
        assertTrue(parsed.lines.any { it.skipReason?.contains("Ignored") == true })
    }

    @Test
    fun detectsRevolutStocksStatement() {
        val csv =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate
            2026-02-17T10:12:51.768Z,ASME,BUY - MARKET,0.00848752,EUR 1178.20,EUR 10,EUR,1.0000
            2026-02-18T00:00:00.000Z,ASME,DIVIDEND,,,1.85,EUR,1.0000
            2026-02-10T09:00:00.000Z,,CASH TOP-UP,,,500,EUR,1.0000
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, parsed.format)
        val buy = parsed.lines.first { it.type == TransactionType.BUY }
        assertEquals("ASME", buy.symbol)
        assertEquals(Currency.EUR, buy.currency)
        assertEquals(TransactionType.DIVIDEND, parsed.lines.first { it.type == TransactionType.DIVIDEND }.type)
        assertEquals(TransactionType.DEPOSIT_CASH, parsed.lines.first { it.type == TransactionType.DEPOSIT_CASH }.type)
    }

    @Test
    fun rejectsUnknownCsv() {
        val parsed = BrokerCsv.parse("foo,bar\n1,2")
        assertNull(parsed.format)
        assertTrue(parsed.error!!.contains("Not a Trading 212"))
    }

    @Test
    fun parseAllDropsAccountBuysWhenTransactionsPresent() {
        val transactions =
            """
            Date,Time,Product,ISIN,Venue,Quantity,Price,Local currency,Value,Value currency,Exchange rate,Transaction costs,Total,Order ID
            2024-03-15,10:15,VWCE,IE00BK5BQT80,XETR,5,118.25,EUR,-591.25,EUR,,1.00,-592.25,ORD1
            """.trimIndent()
        val account =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
            2024-03-15,10:15,2024-03-15,VWCE,IE00BK5BQT80,Buy 5 VWCE @ 118.25,,-591.25,0,ORD1
            2024-04-15,08:00,2024-04-15,VWCE,IE00BK5BQT80,Dividend,,2.50,2.50,D1
            """.trimIndent()
        val parsed = BrokerCsv.parseAll(listOf(transactions, account))
        assertEquals(1, parsed.lines.count { it.type == TransactionType.BUY })
        assertEquals(1, parsed.lines.count { it.type == TransactionType.DIVIDEND })
    }
}

class ImportBrokerCsvUseCaseTest {
    private val importer = ImportBrokerCsvUseCase()
    private val empty = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun trading212ImportFundsBuyAndKeepsFifoSell() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,Vanguard FTSE All-World,10,100.00,EUR,1,1000.00,EUR,B1
            Market sell,2024-06-01 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,Vanguard FTSE All-World,4,110.00,EUR,1,440.00,EUR,S1
            """.trimIndent()
        val result = importer(empty, listOf(csv))
        assertNull(result.error)
        assertTrue(result.fundedDeposits >= 1)
        assertTrue(result.snapshot.transactions.any { it.type == TransactionType.BUY })
        assertTrue(result.snapshot.transactions.any { it.type == TransactionType.SELL })
        val vwce = result.snapshot.assets.first { it.isin == "IE00BK5BQT80" }
        assertEquals(AssetType.ETF, vwce.assetType)
        assertEquals("VWCE.DE", vwce.quoteSymbol)
    }

    @Test
    fun revolutUsdBuyStoresEurPerUsd() {
        val csv =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate,ISIN
            2024-03-01T10:00:00Z,AAPL,BUY - MARKET,2,150.00,276.00,USD,0.92,US0378331005
            """.trimIndent()
        val result = importer(empty, listOf(csv))
        val buy = result.snapshot.transactions.first { it.type == TransactionType.BUY }
        assertEquals(Currency.USD, result.snapshot.assets.first { it.isin == "US0378331005" }.baseCurrency)
        assertTrue(buy.exchangeRateAtExecution.signum() > 0)
    }

    @Test
    fun duplicateImportIsSkipped() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,2,100.00,EUR,200.00,EUR,B1
            """.trimIndent()
        val first = importer(empty, listOf(csv))
        val second = importer(first.snapshot, listOf(csv))
        assertEquals(1, second.duplicates)
        assertEquals(first.snapshot.transactions.size, second.snapshot.transactions.size)
    }

    @Test
    fun unknownFileDoesNotChangeSnapshot() {
        val result = importer(empty, listOf("not,a,broker\n1,2,3"))
        assertTrue(result.error != null)
        assertTrue(result.snapshot.isEmpty)
    }

    @Test
    fun gbxPriceUsesEurTotal() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2024-02-01 09:00:00,GB0002374006,SHEL_UK_EQ,Shell,10,2500.00,GBX,280.00,EUR,B2
            """.trimIndent()
        val result = importer(empty, listOf(csv))
        val buy = result.snapshot.transactions.first { it.type == TransactionType.BUY }
        assertEquals(Currency.EUR, result.snapshot.assets.first { it.isin == "GB0002374006" }.baseCurrency)
        assertEquals(0, bd("28").compareTo(buy.unitPriceNative))
    }
}

class CsvReaderAndDatesTest {
    @Test
    fun quotedCommaStaysInOneCell() {
        val rows = CsvReader.records("a,b\n\"hello, world\",2")
        assertEquals(listOf("hello, world", "2"), rows[1])
    }

    @Test
    fun doubledQuotesUnescape() {
        val rows = CsvReader.records("h\n\"a\"\"b\"")
        assertEquals("a\"b", rows[1][0])
    }

    @Test
    fun parsesEuropeanAndIsoDates() {
        assertEquals(java.time.LocalDate.of(2024, 3, 15), BrokerDates.parse("15-03-2024"))
        assertEquals(java.time.LocalDate.of(2024, 3, 15), BrokerDates.parse("15/03/2024"))
        assertEquals(java.time.LocalDate.of(2024, 3, 15), BrokerDates.parse("2024-03-15 10:15:00"))
        assertEquals(java.time.LocalDate.of(2026, 2, 17), BrokerDates.parse("2026-02-17T10:12:51.768Z"))
    }

    @Test
    fun parseAllEmptyIsError() {
        val parsed = BrokerCsv.parseAll(emptyList())
        assertTrue(parsed.error!!.contains("at least one"))
    }
}
