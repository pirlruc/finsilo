package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.backup.LedgerBackupResult
import com.pirlruc.finsilo.domain.backup.LedgerBackupText
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerCsvCoverageTest {
    @Test
    fun brokerSourceAndFormatMapsEveryValue() {
        assertEquals("Trading 212", BrokerSource.TRADING_212.label)
        assertEquals("DEGIRO", BrokerSource.DEGIRO.label)
        assertEquals("Revolut", BrokerSource.REVOLUT.label)
        assertEquals(BrokerSource.TRADING_212, BrokerCsvFormat.TRADING_212.source)
        assertEquals(BrokerSource.DEGIRO, BrokerCsvFormat.DEGIRO_TRANSACTIONS.source)
        assertEquals(BrokerSource.DEGIRO, BrokerCsvFormat.DEGIRO_ACCOUNT.source)
        assertEquals(BrokerSource.REVOLUT, BrokerCsvFormat.REVOLUT_STOCKS.source)
    }

    @Test
    fun symbolReviewSkipsCashAndKeepsSellDividendInterest() {
        val cash = line(TransactionType.INTEREST, "EUR-CASH", AssetType.CASH)
        val sell = line(TransactionType.SELL, "VWCE.DE", AssetType.ETF, isin = "IE00BK5BQT80")
        val div = line(TransactionType.DIVIDEND, "VWCE.DE", AssetType.ETF, isin = "IE00BK5BQT80")
        val interest = line(TransactionType.INTEREST, "PPR1", AssetType.PPR)
        val blank = line(TransactionType.BUY, "", AssetType.STOCK)
        val skipped = line(null, "X", AssetType.STOCK)
        val buy = line(TransactionType.BUY, "IWDA.AS", AssetType.ETF, isin = "IE00B4L5Y983")
        val withdrawal = line(TransactionType.WITHDRAWAL, "CASH-OUT", AssetType.ETF)
        val drafts = ImportSymbolReview.drafts(listOf(cash, sell, div, interest, blank, skipped, buy, withdrawal))
        assertTrue(drafts.any { it.symbol == "IWDA.AS" })
        assertTrue(drafts.none { it.symbol == "CASH-OUT" })
        assertTrue(drafts.none { it.symbol == "EUR-CASH" })
        assertTrue(drafts.any { it.symbol == "VWCE.DE" })
        assertTrue(drafts.any { it.symbol == "PPR1" })
        assertTrue(drafts.none { it.symbol.isBlank() })
    }

    @Test
    fun untaggedDepositFingerprintFallbacks() {
        val skip = BrokerLines.skip(BrokerCsvFormat.TRADING_212, 1, "Ignored")
        assertEquals("", ImportFingerprints.untaggedDeposit(skip))
        val blankIsin = line(TransactionType.INTEREST, "EUR-CASH", AssetType.CASH, isin = "  ")
        assertTrue(ImportFingerprints.untaggedDeposit(blankIsin).contains("EUR-CASH"))
        val withIsin = line(TransactionType.INTEREST, "EUR-CASH", AssetType.CASH, isin = "EU00CASH")
        assertTrue(ImportFingerprints.untaggedDeposit(withIsin).contains("EU00CASH"))
        val orphan =
            com.pirlruc.finsilo.domain.model.Transaction(
                id = "orphan",
                assetId = "gone",
                date = LocalDate.of(2024, 1, 17),
                type = TransactionType.DEPOSIT_CASH,
                quantity = bd("1"),
                unitPriceNative = bd("1"),
                exchangeRateAtExecution = bd("1"),
                unitPriceEur = bd("1"),
                feesEur = BigDecimal.ZERO,
                source = null,
            )
        val snapshot =
            com.pirlruc.finsilo.domain.model.PortfolioSnapshot(
                assets = emptyList(),
                transactions = listOf(orphan),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        assertTrue(ImportFingerprints.counts(snapshot).keys.single().contains("GONE"))
    }

    @Test
    fun splitCashLayoutDetectsIsinOnlyHeaders() {
        val headers = listOf("Col0", "Col1", "Col2", "Col3", "ISIN", "Col5", "FX", "X", "", "Y", "", "Z")
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(headers))
        assertNull(BrokerCsvDetect.from(listOf("ISIN", "FX")))
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(listOf("Quantité", "Prix", "Produit")))
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(listOf("Stück", "Kurs", "Produkt")))
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, BrokerCsvDetect.from(listOf("Quantità", "Prezzo", "Prodotto")))
        assertNull(BrokerCsvDetect.from(listOf("Description", "Product")))
        assertNull(BrokerCsvDetect.from(listOf("Change", "Balance")))
        val descSplit = List(12) {
            if (it == 5) {
                "Description"
            } else if (it == 8 || it == 10) {
                ""
            } else {
                "C$it"
            }
        }
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, BrokerCsvDetect.from(descSplit))
        val noMatchSplit = List(12) { if (it == 8 || it == 10) "" else "C$it" }
        assertNull(BrokerCsvDetect.from(noMatchSplit))
        val filledEight = List(12) {
            if (it == 4) {
                "ISIN"
            } else if (it == 8) {
                "X"
            } else if (it == 10) {
                ""
            } else {
                "C$it"
            }
        }
        assertNull(BrokerCsvDetect.from(filledEight))
        val filledTen = List(12) {
            if (it == 4) {
                "ISIN"
            } else if (it == 8) {
                ""
            } else if (it == 10) {
                "X"
            } else {
                "C$it"
            }
        }
        assertNull(BrokerCsvDetect.from(filledTen))
        BrokerSource.entries.forEach { source -> assertTrue(source.label.isNotBlank()) }
        assertNull(BrokerCsvDetect.from(listOf("Quantity", "Price")))
        assertNull(BrokerCsvDetect.from(listOf("Quantity", "Price", "Product", "Action")))
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, BrokerCsvDetect.from(listOf("Ticker", "Type", "Price")))
        assertNull(BrokerCsvDetect.from(listOf("Type", "Price per share", "Action")))
    }

    @Test
    fun pairedAmountReadsCurrencyOnEitherSide() {
        val headers = listOf("Change", "", "Name")
        val currencyFirst = CsvRow(headers, listOf("EUR", "12.5", "x"))
        assertEquals("EUR" to "12.5", currencyFirst.pairedAmount("Change"))
        val amountFirst = CsvRow(headers, listOf("12.5", "USD", "x"))
        assertEquals("USD" to "12.5", amountFirst.pairedAmount("Change"))
        val plain = CsvRow(listOf("Change", "Balance"), listOf("1000", "1000"))
        assertEquals("" to "1000", plain.pairedAmount("Change"))
        assertEquals("12.5", CsvRow(listOf("Price per share USD"), listOf("12.5")).get("Price"))
        val prefixPair = CsvRow(listOf("Change EUR", "x"), listOf("EUR", "9"))
        assertEquals("EUR" to "9", prefixPair.pairedAmount("Chan"))
        assertEquals("" to "EUR", CsvRow(listOf("Change", "x"), listOf("EUR", "")).pairedAmount("Change"))
        assertEquals("" to "", CsvRow(listOf("Change", "x"), listOf("", "USD")).pairedAmount("Change"))
        assertEquals("" to "1000", CsvRow(listOf("Change"), listOf("1000")).pairedAmount("Change"))
        assertEquals("" to "", CsvRow(listOf("Change"), listOf("  ")).pairedAmount("Change"))
        assertEquals("" to "", CsvRow(listOf("Other"), listOf("1")).pairedAmount("Missing"))
        assertEquals("", CsvRow(listOf("Other"), listOf("1")).get("Missing"))
        assertEquals("descricao", normalizeHeader("Descrição"))
        assertTrue(looksCurrencyCode("EUR"))
        assertTrue(!looksCurrencyCode("1000"))
    }

    @Test
    fun degiroLocalizedTradesAndRevolutSpanishActions() {
        val degiro =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
            2024-02-01,09:00,2024-02-01,VWCE,IE00BK5BQT80,Achat 2 VWCE @ 50,,-100,1,B1
            2024-02-02,09:00,2024-02-02,VWCE,IE00BK5BQT80,Kauf 1 VWCE,,-50,1,B2
            2024-02-03,09:00,2024-02-03,VWCE,IE00BK5BQT80,Acquisto 1 VWCE,,-50,1,B3
            2024-02-04,09:00,2024-02-04,VWCE,IE00BK5BQT80,Vente 1 VWCE,,50,1,S1
            2024-02-05,09:00,2024-02-05,VWCE,IE00BK5BQT80,Verkauf 1 VWCE,,50,1,S2
            2024-02-06,09:00,2024-02-06,VWCE,IE00BK5BQT80,Vendita 1 VWCE,,50,1,S3
            2024-02-07,09:00,2024-02-07,VWCE,IE00BK5BQT80,Imposto sobre dividendos,,-0.2,1,
            2024-02-08,09:00,2024-02-08,VWCE,IE00BK5BQT80,Dividendo,,1.1,2,D1
            2024-02-09,09:00,2024-02-09,,,Einzahlung,,20,22,
            2024-02-10,09:00,2024-02-10,,,Retrait,,-5,17,
            2024-02-11,09:00,2024-02-11,VWCE,IE00BK5BQT80,Koop 1 VWCE,,-50,1,B4
            2024-02-12,09:00,2024-02-12,VWCE,IE00BK5BQT80,Buy 1 VWCE,,-50,1,B5
            2024-02-13,09:00,2024-02-13,VWCE,IE00BK5BQT80,Compra 1 VWCE,,-50,1,B6
            2024-02-14,09:00,2024-02-14,VWCE,IE00BK5BQT80,Verkoop 1 VWCE,,50,1,S4
            2024-02-15,09:00,2024-02-15,VWCE,IE00BK5BQT80,Sell 1 VWCE,,50,1,S5
            2024-02-16,09:00,2024-02-16,VWCE,IE00BK5BQT80,Venda 1 VWCE,,50,1,S6
            2024-02-17,09:00,2024-02-17,VWCE,IE00BK5BQT80,Dividend,,1,2,D2
            2024-02-18,09:00,2024-02-18,VWCE,IE00BK5BQT80,Dividende,,1,2,D3
            2024-02-19,09:00,2024-02-19,VWCE,IE00BK5BQT80,Dividendbelasting,,-0.1,2,
            2024-02-20,09:00,2024-02-20,VWCE,IE00BK5BQT80,Dividend tax,,-0.1,2,
            """.trimIndent()
        val parsed = BrokerCsv.parse(degiro)
        assertTrue(parsed.lines.count { it.type == TransactionType.BUY } >= 3)
        assertTrue(parsed.lines.count { it.type == TransactionType.SELL } >= 3)
        assertTrue(parsed.lines.any { it.type == TransactionType.DIVIDEND })
        assertTrue(parsed.lines.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(parsed.lines.any { it.type == TransactionType.WITHDRAWAL })
        val revolut =
            """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate
            2026-02-17T10:12:51.768Z,ASME,COMPRA,1,10,10,EUR,1
            2026-02-18T10:12:51.768Z,ASME,VENTA,1,11,11,EUR,1
            2026-02-19T10:12:51.768Z,,JUROS,,,0.40,EUR,1
            2026-02-20T10:12:51.768Z,ASME,DIVIDENDO,,,1,EUR,1
            2026-02-21T10:12:51.768Z,,WITHDRAW,,,2,EUR,1
            2026-02-22T10:12:51.768Z,,CASH TOP-UP,,,3,EUR,1
            2026-02-23T10:12:51.768Z,ASME,FEE,,,1,EUR,1
            2026-02-24T10:12:51.768Z,ASME,VENDA,1,11,11,EUR,1
            """.trimIndent()
        val rev = BrokerCsv.parse(revolut)
        assertTrue(rev.lines.any { it.type == TransactionType.BUY })
        assertTrue(rev.lines.any { it.type == TransactionType.SELL })
        assertTrue(rev.lines.any { it.type == TransactionType.INTEREST })
        assertTrue(rev.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(rev.lines.any { it.skipReason?.contains("Ignored") == true })
    }

    @Test
    fun backupIgnoresUnknownBrokerSourceColumn() {
        val text =
            """
            FSILO-LEDGER-3
            A	asset-cash	EUR-CASH	Euro cash	CASH	EUR		
            T	tx1	asset-cash	2024-01-10	DEPOSIT_CASH	10	1	1	1	0	1	NOT_A_BROKER
            END
            """.trimIndent()
        val restored = LedgerBackupText.decode(text) as LedgerBackupResult.Restored
        assertNull(restored.snapshot.transactions.single().source)
        val blank =
            """
            FSILO-LEDGER-3
            A	asset-cash	EUR-CASH	Euro cash	CASH	EUR		
            T	tx1	asset-cash	2024-01-10	DEPOSIT_CASH	10	1	1	1	0	1	
            END
            """.trimIndent()
        val restoredBlank = LedgerBackupText.decode(blank) as LedgerBackupResult.Restored
        assertNull(restoredBlank.snapshot.transactions.single().source)
    }

    private fun line(
        type: TransactionType?,
        symbol: String,
        assetType: AssetType,
        isin: String? = null,
    ) = BrokerCsvLine(
        date = LocalDate.of(2024, 1, 15),
        type = type,
        skipReason = if (type == null) "Ignored" else null,
        symbol = symbol,
        name = symbol,
        isin = isin,
        quoteSymbol = symbol,
        assetType = assetType,
        quantity = bd("1"),
        unitPriceNative = bd("1"),
        currency = Currency.EUR,
        feesEur = BigDecimal.ZERO,
        eurPerUsd = null,
        sourceLine = 2,
        format = BrokerCsvFormat.TRADING_212,
    )
}
