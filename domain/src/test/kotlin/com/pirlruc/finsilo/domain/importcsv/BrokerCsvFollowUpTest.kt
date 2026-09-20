package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.backup.LedgerBackupResult
import com.pirlruc.finsilo.domain.backup.LedgerBackupText
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrokerCsvFollowUpTest {
    private val importer = ImportBrokerCsvUseCase()
    private val empty = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun degiroSplitCurrencyAccountAndPortugueseHeaders() {
        val english =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,,Balance,,Order Id
            01-02-2024,09:00,01-02-2024,,,Deposit,,EUR,1000,EUR,1000,
            02-02-2024,09:00,02-02-2024,,,Flatex Interest,,EUR,1.25,EUR,1001.25,
            03-02-2024,09:00,03-02-2024,VWCE,IE00BK5BQT80,Dividend,,EUR,2.50,EUR,1003.75,D1
            """.trimIndent()
        val parsed = BrokerCsv.parse(english)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, parsed.format)
        assertEquals(TransactionType.DEPOSIT_CASH, parsed.lines.first { it.type == TransactionType.DEPOSIT_CASH }.type)
        assertEquals(0, bd("1000").compareTo(parsed.lines.first { it.type == TransactionType.DEPOSIT_CASH }.quantity))
        assertEquals(TransactionType.INTEREST, parsed.lines.first { it.type == TransactionType.INTEREST }.type)
        val portuguese =
            """
            Data;Hora;Data valor;Produto;ISIN;Descrição;FX;Alteração;;Saldo;;ID da ordem
            01-02-2024;09:00;01-02-2024;;;Depósito;;EUR;500;EUR;500;
            02-02-2024;09:00;02-02-2024;;;Levantamento;;EUR;-50;EUR;450;
            03-02-2024;09:00;03-02-2024;;;Juros;;EUR;0,40;EUR;450,40;
            """.trimIndent()
        val pt = BrokerCsv.parse(portuguese)
        assertEquals(BrokerCsvFormat.DEGIRO_ACCOUNT, pt.format)
        assertTrue(pt.lines.any { it.type == TransactionType.DEPOSIT_CASH })
        assertTrue(pt.lines.any { it.type == TransactionType.WITHDRAWAL })
        assertTrue(pt.lines.any { it.type == TransactionType.INTEREST })
    }

    @Test
    fun degiroTransactionsBlankCurrencyColumns() {
        val csv =
            """
            Date,Time,Product,ISIN,Venue,Quantity,Price,,Value,,Exchange rate,Transaction costs,Total,Order ID
            15-03-2024,10:15,VANGUARD FTSE ALL-WORLD,IE00BK5BQT80,XETR,5,118.25,EUR,-591.25,EUR,,1.00,-592.25,ORD1
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.DEGIRO_TRANSACTIONS, parsed.format)
        val buy = parsed.lines.single { it.type == TransactionType.BUY }
        assertEquals(0, bd("5").compareTo(buy.quantity))
        assertEquals(0, bd("118.25").compareTo(buy.unitPriceNative))
    }

    @Test
    fun revolutInterestAndSpanishHeaders() {
        val csv =
            """
            Fecha,Ticker,Tipo,Cantidad,Precio por acción,Importe total,Divisa,Tipo de cambio
            2026-02-10T09:00:00.000Z,,CASH TOP-UP,,,500,EUR,1.0000
            2026-02-11T09:00:00.000Z,,CASH INTEREST,,,1.10,EUR,1.0000
            2026-02-17T10:12:51.768Z,ASME,BUY - MARKET,0.5,20.00,10,EUR,1.0000
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(BrokerCsvFormat.REVOLUT_STOCKS, parsed.format)
        assertEquals(TransactionType.INTEREST, parsed.lines.first { it.type == TransactionType.INTEREST }.type)
        assertEquals(TransactionType.DEPOSIT_CASH, parsed.lines.first { it.type == TransactionType.DEPOSIT_CASH }.type)
    }

    @Test
    fun trading212InterestIsCashSweepNotDeposit() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Deposit,2024-01-10 09:00:00,,,,,,,1000.00,EUR,DEP1
            Interest on cash,2024-01-17 10:30:00,,,,,,,1.00,EUR,INT1
            """.trimIndent()
        val parsed = BrokerCsv.parse(csv)
        assertEquals(1, parsed.lines.count { it.type == TransactionType.DEPOSIT_CASH })
        assertEquals(1, parsed.lines.count { it.type == TransactionType.INTEREST })
        val result = importer(empty, listOf(csv))
        assertEquals(BrokerSource.TRADING_212, result.snapshot.transactions.first { it.type == TransactionType.INTEREST }.source)
        val again = importer(result.snapshot, listOf(csv))
        assertEquals(2, again.duplicates)
        assertEquals(result.snapshot.transactions.size, again.snapshot.transactions.size)
    }

    @Test
    fun secondBrokerImportAddsNewRowsAfterTrading212() {
        val t212 =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Deposit,2024-01-10 09:00:00,,,,,,,1000.00,EUR,DEP1
            Market buy,2024-01-15 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,2,100.00,EUR,200.00,EUR,B1
            """.trimIndent()
        val first = importer(empty, listOf(t212))
        val degiro =
            """
            Date,Time,Value date,Product,ISIN,Description,FX,Change,,Balance,,Order Id
            01-03-2024,09:00,01-03-2024,,,Deposit,,EUR,300,EUR,300,
            """.trimIndent()
        val second = importer(first.snapshot, listOf(degiro))
        assertEquals(0, second.duplicates)
        assertTrue(second.accepted >= 1)
        assertTrue(second.snapshot.transactions.any { it.source == BrokerSource.DEGIRO })
        val third = importer(second.snapshot, listOf(degiro))
        assertTrue(third.duplicates >= 1)
        assertEquals(second.snapshot.transactions.size, third.snapshot.transactions.size)
    }

    @Test
    fun previouslyBookedInterestDepositIsNotDuplicated() {
        val asDeposit =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Deposit,2024-01-17 10:30:00,,,,,,,1.00,EUR,INT1
            """.trimIndent()
        val first = importer(empty, listOf(asDeposit))
        val asInterest =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Interest on cash,2024-01-17 10:30:00,,,,,,,1.00,EUR,INT1
            """.trimIndent()
        val second = importer(first.snapshot, listOf(asInterest))
        assertEquals(1, second.duplicates)
        assertEquals(first.snapshot.transactions.size, second.snapshot.transactions.size)
    }

    @Test
    fun backupRoundTripKeepsBrokerSource() {
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Deposit,2024-01-10 09:00:00,,,,,,,1000.00,EUR,DEP1
            """.trimIndent()
        val imported = importer(empty, listOf(csv))
        val restored = LedgerBackupText.decode(LedgerBackupText.encode(imported.snapshot)) as LedgerBackupResult.Restored
        assertEquals(BrokerSource.TRADING_212, restored.snapshot.transactions.single { it.type == TransactionType.DEPOSIT_CASH }.source)
    }
}
