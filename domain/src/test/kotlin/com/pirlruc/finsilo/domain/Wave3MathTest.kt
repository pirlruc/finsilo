package com.pirlruc.finsilo.domain

import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.importcsv.Trading212History
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.BenchmarkInvestment
import com.pirlruc.finsilo.domain.performance.DatedCashflow
import com.pirlruc.finsilo.domain.performance.DividendCadence
import com.pirlruc.finsilo.domain.performance.DividendCalendar
import com.pirlruc.finsilo.domain.performance.IrsAnnualCsv
import com.pirlruc.finsilo.domain.performance.IrsAnnualReport
import com.pirlruc.finsilo.domain.performance.IrsQuadro
import com.pirlruc.finsilo.domain.performance.ListingCountry
import com.pirlruc.finsilo.domain.performance.PriceObservation
import com.pirlruc.finsilo.domain.performance.Xirr
import com.pirlruc.finsilo.domain.portfolio.CashShortfall
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Wave3MathTest {
    private val start = LocalDate.of(2023, 1, 1)
    private val end = LocalDate.of(2024, 1, 1)

    @Test
    fun xirrMatchesAOneYearTenPercentAndRefusesNoSolution() {
        val rate = Xirr.annual(listOf(DatedCashflow(start, BigDecimal("-1000")), DatedCashflow(end, BigDecimal("1100"))))
        assertEquals(0, BigDecimal("0.10").compareTo(rate!!.setScale(2, java.math.RoundingMode.HALF_UP)))
        assertNull(Xirr.annual(listOf(DatedCashflow(start, BigDecimal("100")), DatedCashflow(end, BigDecimal("50")))))
        assertNull(Xirr.annual(listOf(DatedCashflow(start, BigDecimal("-100")))))
        assertNull(Xirr.annual(emptyList()))
    }

    @Test
    fun benchmarkUsesTheCallerSeries() {
        val prices = listOf(PriceObservation(start, BigDecimal("10")), PriceObservation(end, BigDecimal("11")))
        val flows = listOf(DatedCashflow(start, BigDecimal("-100")))
        val terminal = BenchmarkInvestment.terminalValue(flows, prices, end)
        assertEquals(0, BigDecimal("110").compareTo(terminal!!.setScale(0, java.math.RoundingMode.HALF_UP)))
        assertTrue(BenchmarkInvestment.comparisonRate(flows, prices, end)!!.signum() > 0)
        assertNull(BenchmarkInvestment.terminalValue(flows, emptyList(), end))
        assertNull(BenchmarkInvestment.terminalValue(listOf(DatedCashflow(start.minusDays(10), BigDecimal("-1"))), prices, end))
        val sellTooMuch = listOf(DatedCashflow(start, BigDecimal("-100")), DatedCashflow(end, BigDecimal("1000")))
        assertNull(BenchmarkInvestment.terminalValue(sellTooMuch, prices, end))
    }

    @Test
    fun dividendCadencesAndIrregularHistory() {
        val from = LocalDate.of(2024, 6, 1)
        assertEquals(DividendCadence.MONTHLY, project(listOf("2024-01-15", "2024-02-14", "2024-03-15"), from)?.cadence)
        assertEquals(DividendCadence.QUARTERLY, project(listOf("2024-01-15", "2024-04-15", "2024-07-15"), from)?.cadence)
        assertEquals(DividendCadence.SEMIANNUAL, project(listOf("2023-01-15", "2023-07-16", "2024-01-15"), from)?.cadence)
        assertEquals(DividendCadence.ANNUAL, project(listOf("2021-03-01", "2022-03-01", "2023-03-01"), from)?.cadence)
        assertNull(project(listOf("2024-01-01", "2024-01-20", "2024-06-01"), from))
        assertNull(DividendCalendar.project(listOf(start, end), BigDecimal.ONE, from))
        val forward = project(listOf("2024-01-15", "2024-04-15", "2024-07-15"), LocalDate.of(2024, 7, 15))
        assertEquals(0, BigDecimal("8").compareTo(forward!!.forwardIncomeEur))
    }

    @Test
    fun irsRowsFollowCountryAndCsvIsNotXml() {
        assertEquals("DE", ListingCountry.code("VWCE.DE"))
        assertEquals("PT", ListingCountry.code("EDP.LS"))
        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("AAPL"))
        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("NOPE.ZZ"))
        val de = asset("vwce", "VWCE.DE")
        val pt = asset("edp", "EDP.LS")
        val snapshot = PortfolioSnapshot(
            listOf(de, pt),
            listOf(
                buy("b1", de.id, LocalDate.of(2024, 1, 2), "10"),
                dividend("d1", de.id, LocalDate.of(2024, 6, 1), "2"),
                sell("s1", de.id, LocalDate.of(2024, 8, 1), "10", "12"),
                buy("b2", pt.id, LocalDate.of(2024, 1, 3), "5"),
                sell("s2", pt.id, LocalDate.of(2024, 9, 1), "5", "6"),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val rows = IrsAnnualReport().rows(snapshot, 2024)
        assertTrue(rows.any { it.quadro == IrsQuadro.ANEXO_J_8A && it.countryCode == "DE" && it.year == 2024 })
        assertTrue(rows.any { it.quadro == IrsQuadro.ANEXO_J_9_2A && it.countryCode == "DE" })
        assertTrue(rows.any { it.quadro == IrsQuadro.ANEXO_G_9 && it.countryCode == "PT" })
        val forced = IrsAnnualReport().rows(snapshot, 2024) { "XX" }
        assertTrue(forced.none { it.quadro == IrsQuadro.ANEXO_G_9 })
        val csv = IrsAnnualCsv.write(rows)
        assertTrue(csv.startsWith("quadro,country,"))
        assertTrue(csv.contains("ANEXO_J_8A"))
        assertEquals("quadro,country,gross_income_eur,foreign_tax_withheld_eur,acquisition_value_eur,realization_value_eur,year", IrsAnnualCsv.write(emptyList()))
        assertTrue(rows.none { it.grossIncomeEur.toString().contains("<") })
    }

    @Test
    fun trading212HistoryMapsFillsAndRefusesOrderPlacementPaths() {
        val orders = """
            {"items":[{"order":{"id":10,"ticker":"VWCE_DE_EQ","side":"BUY","status":"FILLED"},
            "fill":{"id":99,"quantity":2,"price":100,"filledAt":"2024-02-01T10:00:00Z","type":"BUY",
            "walletImpact":{"currency":"EUR","netValue":200,"fxRate":1,"fees":[{"quantity":0.5}]}}},
            {"order":{"status":"NEW"},"fill":null}],"nextPagePath":"/api/v0/equity/history/orders?cursor=2"}
        """.trimIndent()
        val dividends = """
            {"items":[{"ticker":"VWCE_DE_EQ","amount":1.5,"quantity":2,"grossAmountPerShare":0.75,
            "paidOn":"2024-06-01T00:00:00Z","reference":"div-1","currency":"EUR"}],"nextPagePath":null}
        """.trimIndent()
        val cash = """
            {"items":[{"type":"DEPOSIT","amount":500,"reference":"dep-1","dateTime":"2024-01-15T00:00:00Z"},
            {"type":"FEE","amount":1,"reference":"fee-1","dateTime":"2024-01-16T00:00:00Z"}],"nextPagePath":null}
        """.trimIndent()
        val lines = Trading212History.lines(listOf(orders), listOf(dividends), listOf(cash))
        val buy = lines.single { it.type == TransactionType.BUY }
        assertEquals("VWCE.DE", buy.quoteSymbol)
        assertEquals("99", buy.brokerReference)
        assertEquals(0, BigDecimal("0.5").compareTo(buy.feesEur))
        assertTrue(lines.any { it.skipReason?.contains("NEW") == true })
        assertTrue(lines.any { it.type == TransactionType.DIVIDEND && it.brokerReference == "div-1" })
        assertTrue(lines.any { it.type == TransactionType.DEPOSIT_CASH && it.brokerReference == "dep-1" })
        assertTrue(lines.any { it.skipReason?.contains("FEE") == true })
        assertEquals("/api/v0/equity/history/orders?cursor=2", Trading212History.nextPath(orders))
        assertNull(Trading212History.nextPath("""{"nextPagePath":null}"""))
        assertEquals("/api/v0/equity/history/orders?cursor=2", Trading212History.cursorPath("https://live.trading212.com/api/v0/equity/history/orders?cursor=2"))
        assertNull(Trading212History.cursorPath("/api/v0/equity/orders"))
        assertNull(Trading212History.cursorPath("https://evil.example/api/v0/equity/history/orders"))
        assertNull(Trading212History.cursorPath(""))
        val again = ImportBrokerCsvUseCase().importLines(imported(buy), listOf(buy))
        assertTrue(again.duplicates >= 1)
        assertEquals(listOf("t212-99"), again.snapshot.transactions.filter { it.type == TransactionType.BUY }.map { it.id })
    }

    @Test
    fun commodityUsesPairedEurAndFxBandStaysOnUsd() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Exchanged to XAU,0.1,0,XAU,COMPLETED,0.1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Cash leg,250,0,EUR,COMPLETED,10
        """.trimIndent()
        val gold = BrokerCsv.parse(csv).lines.single { it.symbol == "XAU" }
        assertEquals(0, BigDecimal("2500").compareTo(gold.unitPriceNative))
        val gbp = BrokerMoney.book(MoneyParts("2", "10", "GBP", "18", "EUR", "1.20", "0", "EUR"))
        assertEquals(0, BigDecimal("0.90").compareTo(gbp!!.eurPerUsd))
        val usd = BrokerMoney.book(MoneyParts("1", "10", "USD", "", "", "1.20", "0", "EUR"))
        assertTrue(usd!!.eurPerUsd!! < BigDecimal.ONE)
        assertThrows(CashShortfall::class.java) {
            PositionLedger().cashEur(
                listOf(tx("b", TransactionType.BUY, BigDecimal("10"))),
                mapOf("a" to asset("a", "AAA")),
            )
        }
    }

    @Test
    fun feeAndCashBranchesStayExplicit() {
        assertFalse(BrokerMoney.unconvertibleFee(MoneyParts("1", "10", "EUR", "10", "EUR", "", "1", "EUR")))
        assertEquals(0, BigDecimal.ZERO.compareTo(BrokerMoney.book(MoneyParts("1", "10", "EUR", "10", "EUR", "", "nope", "USD"))!!.feesEur))
        listOf("CHF", "DKK", "SEK", "NOK").forEach { code ->
            val booked = BrokerMoney.book(MoneyParts("1", "10", "EUR", "10", "EUR", "0.5", "2", code))
            assertTrue(booked!!.feesEur.signum() > 0)
            assertNotNull(BrokerMoney.toEurCash(BigDecimal.TEN, code, "0.5"))
        }
        assertNotNull(BrokerMoney.book(MoneyParts("1", "", "GBP", "20", "EUR", "", "0", "EUR")))
        assertNull(BrokerMoney.book(MoneyParts("1", "", "GBX", "", "", "", "0", "EUR")))
        assertNull(BrokerMoney.toEurCash(BigDecimal.TEN, "USD", ""))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.TEN, "USD", "1.20"))
        assertNull(BrokerMoney.plainFxRate("0"))
        assertNull(BrokerMoney.plainFxRate("abc"))
        val zeroTotal = BrokerMoney.book(MoneyParts("1", "10", "GBP", "0", "EUR", "1.1", "0", "EUR"))
        assertEquals(0, BigDecimal("1.1").compareTo(zeroTotal!!.eurPerUsd))
        val ledger = PositionLedger()
        val day = LocalDate.of(2024, 1, 1)
        val rates = listOf(com.pirlruc.finsilo.domain.model.CurrencyRate(day, BigDecimal("0.9")))
        assertNull(ledger.eurPerUnitOn(day, rates, Currency.GBP))
        assertNull(ledger.nativePrice("missing", day, emptyMap(), emptyList()))
        val dividend = tx("d", "a", day, TransactionType.DIVIDEND, BigDecimal.ONE)
        assertNull(ledger.nativePrice("a", day, emptyMap(), listOf(dividend)))
    }

    @Test
    fun commodityPrefersNamedCashLeg() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Exchanged to XAG,1,0,XAG,COMPLETED,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Cash,10,0,EUR,,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,Paid for XAG,40,0,EUR,COMPLETED,1
            Exchange,Current,2024-05-02 9:15:30,2024-05-02 9:16:00,Other,5,0,EUR,COMPLETED,1
        """.trimIndent()
        val silver = BrokerCsv.parse(csv).lines.single { it.symbol == "XAG" }
        assertEquals(0, BigDecimal("40").compareTo(silver.unitPriceNative))
    }

    private fun project(dates: List<String>, from: LocalDate) = DividendCalendar.project(dates.map(LocalDate::parse), BigDecimal("2"), from)

    private fun imported(line: BrokerCsvLine) = ImportBrokerCsvUseCase().importLines(PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), listOf(line)).snapshot

    private fun asset(id: String, symbol: String) = Asset(id, symbol, symbol, AssetType.ETF, Currency.EUR)

    private fun buy(id: String, assetId: String, date: LocalDate, price: String) = tx(id, assetId, date, TransactionType.BUY, BigDecimal(price))

    private fun sell(id: String, assetId: String, date: LocalDate, qty: String, price: String) = tx(id, assetId, date, TransactionType.SELL, BigDecimal(price), BigDecimal(qty))

    private fun dividend(id: String, assetId: String, date: LocalDate, amount: String) = tx(id, assetId, date, TransactionType.DIVIDEND, BigDecimal(amount), BigDecimal(amount))

    private fun tx(id: String, type: TransactionType, price: BigDecimal) = tx(id, "a", start, type, price, BigDecimal.ONE)

    private fun tx(id: String, assetId: String, date: LocalDate, type: TransactionType, price: BigDecimal, qty: BigDecimal = BigDecimal.ONE) = Transaction(id, assetId, date, type, qty, price, BigDecimal.ONE, price, BigDecimal.ZERO)
}
