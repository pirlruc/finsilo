package com.pirlruc.finsilo.domain

import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.ImportFingerprints
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.importcsv.Trading212History
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerReference
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.BenchmarkInvestment
import com.pirlruc.finsilo.domain.performance.DatedCashflow
import com.pirlruc.finsilo.domain.performance.DividendCalendar
import com.pirlruc.finsilo.domain.performance.IrsAnnualReport
import com.pirlruc.finsilo.domain.performance.PriceObservation
import com.pirlruc.finsilo.domain.performance.Xirr
import com.pirlruc.finsilo.domain.portfolio.CashShortfall
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Wave3CoverageTest {
    private val day = LocalDate.of(2020, 1, 1)

    @Test
    fun xirrBisectsWhenNewtonLeavesTheBracket() {
        val flows = listOf(
            DatedCashflow(day, BigDecimal("-50")),
            DatedCashflow(day, BigDecimal.ZERO),
            DatedCashflow(day.plusDays(180), BigDecimal("-50")),
            DatedCashflow(day.plusDays(365), BigDecimal("5")),
        )
        assertTrue(Xirr.annual(flows)!!.signum() < 0)
        val belowFloor = listOf(
            DatedCashflow(day, BigDecimal("-50")),
            DatedCashflow(day.plusDays(180), BigDecimal("-50")),
            DatedCashflow(day.plusDays(365), BigDecimal("30")),
        )
        assertTrue(Xirr.annual(belowFloor)!!.signum() < 0)
        val sameDay = listOf(DatedCashflow(day, BigDecimal("-100")), DatedCashflow(day, BigDecimal("110")))
        assertNull(Xirr.annual(sameDay))
    }

    @Test
    fun benchmarkSkipsAMissingTerminalPrice() {
        val end = day.plusDays(10)
        val prices = listOf(PriceObservation(end, BigDecimal.ZERO), PriceObservation(end, BigDecimal("10")))
        val flows = listOf(DatedCashflow(end, BigDecimal("-10")))
        assertNull(BenchmarkInvestment.comparisonRate(flows, emptyList(), end))
        assertNull(BenchmarkInvestment.terminalValue(flows, prices, day))
        assertNotNull(BenchmarkInvestment.terminalValue(flows, prices, end))
    }

    @Test
    fun dividendProjectionStopsWhenTheGapCannotBeWalked() {
        val dates = listOf(LocalDate.of(2020, 1, 15), LocalDate.of(2020, 2, 14), LocalDate.of(2020, 3, 15))
        assertNull(DividendCalendar.project(dates, BigDecimal.ONE, LocalDate.of(2024, 6, 1)))
        val from = LocalDate.of(2024, 6, 1)
        val recent = DividendCalendar.project(listOf(from.minusDays(89), from.minusDays(59), from.minusDays(29)), BigDecimal("2"), from)
        assertEquals(0, BigDecimal("24").compareTo(recent!!.forwardIncomeEur))
    }

    @Test
    fun irsSkipsDividendsOutsideTheYearOrTheBook() {
        val asset = Asset("a", "VWCE.DE", "VWCE", AssetType.ETF, Currency.EUR)
        val snapshot = PortfolioSnapshot(
            listOf(asset),
            listOf(
                tx("old", "a", LocalDate.of(2023, 6, 1), TransactionType.DIVIDEND, null),
                tx("gone", "missing", LocalDate.of(2024, 6, 1), TransactionType.DIVIDEND, null),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        assertTrue(IrsAnnualReport().rows(snapshot, 2024).isEmpty())
    }

    @Test
    fun trading212HistoryCoversCursorsFeesAndRefusals() {
        val orders = """
            {"items":[
              {"order":{"id":"ord 1","ticker":"   ","side":"SELL"},
               "fill":{"quantity":"1","price":"","filledAt":"2024-03-01",
               "walletImpact":{"netValue":"","fees":[{"quantity":"1.25"},1,{"quantity":null}]}}},
              {"order":{"ticker":{"nested":true},"status":"FILLED"},"fill":null},
              {"order":{"status":"FILLED","ticker":"AAPL_US_EQ","side":"SELL"},
               "fill":{"id":"s1","quantity":1,"price":10,"filledAt":"nope","type":"SELL",
               "walletImpact":{"currency":"USD","fxRate":"1.20","fees":{"quantity":1}}}}
            ],"nextPagePath":"  "}
        """.trimIndent()
        val dividends = """
            {"items":[
              {"amount":"2","paidOn":"2024-05-01","currency":"","reference":"div 2"},
              {"ticker":"VWCE_DE_EQ","paidOn":"nope","grossAmountPerShare":{"x":1}},
              5
            ]}
        """.trimIndent()
        val cash = """
            {"items":[
              {"type":"withdrawal","amount":"0","dateTime":"2024-04-01","currency":"","reference":"w1"},
              {"type":"Interest","amount":"3","dateTime":"2024-04-02T00:00:00Z","currency":"USD","fxRate":"0.90"},
              {"type":"DEPOSIT","dateTime":"nope"},
              {"amount":"4","dateTime":"2024-04-03"}
            ]}
        """.trimIndent()
        val lines = Trading212History.lines(
            listOf(orders, """{"items":[]}"""),
            listOf(dividends),
            listOf(cash, "[]"),
        )
        assertTrue(lines.any { it.skipReason != null })
        assertTrue(lines.any { it.skipReason?.contains("Unreadable") == true })
        assertEquals(0, Trading212History.lines(emptyList(), emptyList(), emptyList()).size)
        assertNull(Trading212History.nextPath("[]"))
        assertNull(Trading212History.nextPath("""{"nextPagePath":{"a":1}}"""))
        assertNull(Trading212History.cursorPath("   "))
        assertNull(Trading212History.cursorPath("/api/v0/equity/account/summary"))
        val dividendsCursor = "/api/v0/equity/history/dividends?cursor=1"
        assertEquals(dividendsCursor, Trading212History.cursorPath(dividendsCursor))
        val bare = """
            {"items":[{"order":{"id":"only"},"fill":{"price":"8","filledAt":"","walletImpact":{}}}]}
        """.trimIndent()
        val income = """
            {"items":[
              {"ticker":"VWCE_DE_EQ","grossAmountPerShare":"0.4","amount":"1","paidOn":"2024-05-02","currency":"USD","quantity":"2"},
              {"ticker":"VWCE_DE_EQ","paidOn":"2024-05-03","currency":"EUR"}
            ]}
        """.trimIndent()
        val quietCash = """{"items":[{"type":"DEPOSIT","dateTime":"2024-05-04","currency":"EUR"}]}"""
        val extra = Trading212History.lines(listOf("""{"items":[]}""", bare), listOf(income), listOf(quietCash))
        assertTrue(extra.isNotEmpty())
    }

    @Test
    fun moneyAndCommodityEdgesStayExplicit() {
        assertNotNull(BrokerMoney.book(MoneyParts("1", "100", "GBX", "", "", "1.20", "5", "GBX")))
        assertNotNull(BrokerMoney.book(MoneyParts("1", "10", "USD", "", "", "0.90", "1", "USD")))
        assertNull(BrokerMoney.toEurCash(BigDecimal.ZERO, "JPY", ""))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "", ""))
        assertNotNull(BrokerMoney.toEurCash(BigDecimal.ONE, "GBX", "2"))
        assertNull(BrokerMoney.toEurCash(BigDecimal.ONE, "GBX", ""))
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,spent 0 EUR,1,0,XAU,COMPLETED,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,bought XPT,1,0,,COMPLETED,1
            Exchange,Current,2024-05-01 9:15:30,2024-05-01 9:16:00,cash,0,0,USD,COMPLETED,1
            Exchange,Current,2024-05-03 9:15:30,2024-05-03 9:16:00,no metal,1,0,,COMPLETED,1
        """.trimIndent()
        val lines = BrokerCsv.parse(csv).lines
        assertEquals(2, lines.count { it.skipReason?.contains("no EUR") == true })
        assertEquals(2, lines.count { it.skipReason?.contains("missing metal") == true })
    }

    @Test
    fun brokerReferenceAndFingerprintsKeepTheSyncKey() {
        assertNull(BrokerReference.canonical(null))
        assertNull(BrokerReference.canonical("!!!"))
        assertEquals(80, BrokerReference.canonical("a".repeat(90))!!.length)
        assertNull(BrokerReference.ledgerId(" "))
        assertEquals("t212-ab", BrokerReference.ledgerId("ab"))
        assertNull(BrokerReference.fromLedgerId("plain"))
        assertNull(BrokerReference.fromLedgerId("t212-"))
        assertEquals("ab", BrokerReference.fromLedgerId("t212-ab"))
        val asset = Asset("a", "AAA", "AAA", AssetType.STOCK, Currency.EUR, isin = " ")
        val tagged = tx("t212-abc", "a", day, TransactionType.BUY, BrokerSource.TRADING_212)
        val other = tx("t212-zzz", "missing", day, TransactionType.BUY, BrokerSource.DEGIRO)
        val plain = tx("plain", "missing", day, TransactionType.BUY, null)
        val book = PortfolioSnapshot(listOf(asset), listOf(tagged, other, plain), emptyList(), emptyList(), emptyList())
        val counts = ImportFingerprints.counts(book)
        assertTrue(counts.keys.any { it == "ref|TRADING_212|abc" })
        assertTrue(counts.keys.none { it.startsWith("ref|DEGIRO") })
    }

    @Test
    fun sellWhoseFeesExceedProceedsDoesNotClampCash() {
        val asset = Asset("a", "AAA", "AAA", AssetType.STOCK, Currency.EUR)
        val deposit = tx("d", "cash", day, TransactionType.DEPOSIT_CASH, null)
        val buy = tx("b", "a", day, TransactionType.BUY, null)
        val sell = tx("s", "a", day, TransactionType.SELL, null).copy(feesEur = BigDecimal("30"))
        assertThrows(CashShortfall::class.java) {
            PositionLedger().cashEur(listOf(deposit, buy, sell), mapOf("a" to asset))
        }
        val quote = DailyMarketData("a", day, BigDecimal("4"))
        val price = PositionLedger().nativePrice("a", day, mapOf("a" to listOf(quote)), emptyList())
        assertEquals(0, BigDecimal("4").compareTo(price))
    }

    private fun tx(id: String, assetId: String, date: LocalDate, type: TransactionType, source: BrokerSource?) = Transaction(
        id,
        assetId,
        date,
        type,
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ONE,
        BigDecimal.TEN,
        BigDecimal.ZERO,
        source = source,
    )
}
