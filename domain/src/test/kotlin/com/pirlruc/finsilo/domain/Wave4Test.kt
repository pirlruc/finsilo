package com.pirlruc.finsilo.domain

import com.pirlruc.finsilo.domain.backup.LedgerBackupParse
import com.pirlruc.finsilo.domain.backup.LedgerBackupResult
import com.pirlruc.finsilo.domain.entitlement.FlagEntitlements
import com.pirlruc.finsilo.domain.entitlement.PremiumFeature
import com.pirlruc.finsilo.domain.entitlement.PremiumFlag
import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.Trading212History
import com.pirlruc.finsilo.domain.importcsv.WithholdingTax
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.DatedCashflow
import com.pirlruc.finsilo.domain.performance.DividendCalendar
import com.pirlruc.finsilo.domain.performance.DividendReinvestment
import com.pirlruc.finsilo.domain.performance.FirePlanner
import com.pirlruc.finsilo.domain.performance.IrsAnnualCsv
import com.pirlruc.finsilo.domain.performance.IrsAnnualReport
import com.pirlruc.finsilo.domain.performance.ListingCountry
import com.pirlruc.finsilo.domain.performance.RebalanceSimulator
import com.pirlruc.finsilo.domain.performance.Xirr
import com.pirlruc.finsilo.domain.vault.VaultKeys
import com.pirlruc.finsilo.domain.vault.VaultRegistry
import com.pirlruc.finsilo.domain.vault.VaultSelection
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Wave4Test {
    @Test
    fun entitlementFlagGatesOnlyTheNewFeatures() {
        assertEquals(
            setOf(PremiumFeature.MULTI_PORTFOLIO, PremiumFeature.REBALANCE, PremiumFeature.FIRE_DRIP),
            PremiumFeature.entries.toSet(),
        )
        val locked = FlagEntitlements(false)
        val open = FlagEntitlements(true)
        PremiumFeature.entries.forEach { feature ->
            assertFalse(locked.allows(feature))
            assertTrue(open.allows(feature))
        }
        assertTrue(PremiumFlag.parse(null))
        assertTrue(PremiumFlag.parse(""))
        assertTrue(PremiumFlag.parse("true"))
        assertFalse(PremiumFlag.parse("false"))
        assertFalse(PremiumFlag.parse("FALSE"))
    }

    @Test
    fun defaultVaultKeepsTheOriginalDatabaseFile() {
        assertEquals("finsilo.db", VaultRegistry.decode("").single().fileName)
        val rewritten = VaultRegistry.decode("default|moved.db|Personal\nfamily|elsewhere.db|Family\n")
        assertEquals("finsilo.db", rewritten.first { it.id == "default" }.fileName)
        assertEquals("finsilo-family.db", rewritten.first { it.id == "family" }.fileName)
        val added = VaultRegistry.add(VaultRegistry.missing(), "Family")
        assertEquals("finsilo-family.db", added!!.fileName)
        assertEquals("finsilo.db", VaultRegistry.fileName("default"))
        assertNull(VaultRegistry.add(listOf(added), "Family"))
        assertEquals("sqlcipher_wrap_pin", VaultKeys.of("default", "sqlcipher_wrap_pin"))
        assertEquals("vault.family.sqlcipher_wrap_pin", VaultKeys.of("family", "sqlcipher_wrap_pin"))
        val roundTrip = VaultRegistry.decode(VaultRegistry.encode(VaultRegistry.missing() + added))
        assertTrue(roundTrip.any { it.id == "default" && it.fileName == "finsilo.db" })
    }

    @Test
    fun rebalanceRoundsToCentsAndCanStayBuyOnly() {
        val values = mapOf("ETF" to BigDecimal("60.00"), "STOCK" to BigDecimal("40.00"))
        val weights = mapOf("ETF" to BigDecimal("50"), "STOCK" to BigDecimal("50"))
        val both = RebalanceSimulator.trades(values, weights, buyOnly = false)
        assertEquals(0, BigDecimal("-10.00").compareTo(both.first { it.id == "ETF" }.amountEur))
        assertEquals(0, BigDecimal("10.00").compareTo(both.first { it.id == "STOCK" }.amountEur))
        val fee = RebalanceSimulator.trades(values, weights, buyOnly = false, feeEur = BigDecimal("1.00"))
        val feeSum = fee.fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.amountEur) }
        assertEquals(0, BigDecimal("-1.00").compareTo(feeSum))
        val buys = RebalanceSimulator.trades(values, weights, buyOnly = true)
        assertTrue(buys.all { it.amountEur.signum() > 0 })
        assertEquals("STOCK", buys.single().id)
        assertTrue(RebalanceSimulator.trades(emptyMap(), weights, false).isEmpty())
    }

    @Test
    fun fireAndDripCarryTheAdviceNote() {
        val number = FirePlanner.number(BigDecimal("40000"), BigDecimal("0.04"))
        assertEquals(0, BigDecimal("1000000.00").compareTo(number))
        val years = FirePlanner.years(BigDecimal("500000"), number!!, BigDecimal("20000"), BigDecimal.ZERO)
        assertEquals(0, BigDecimal("25.00").compareTo(years))
        val plan = FirePlanner.plan(BigDecimal("500000"), BigDecimal("40000"), BigDecimal("0.04"), BigDecimal("20000"), BigDecimal.ZERO)
        assertEquals(FirePlanner.NOTE, plan!!.note)
        assertTrue(plan.note.contains("Not financial advice"))
        assertNull(FirePlanner.number(BigDecimal.ZERO, BigDecimal("0.04")))
        val from = LocalDate.of(2024, 7, 15)
        val dates = listOf("2024-01-15", "2024-04-15", "2024-07-15").map(LocalDate::parse)
        val drip = DividendReinvestment.project(dates, BigDecimal("2"), from)
        assertEquals(0, BigDecimal("8").compareTo(drip!!.forwardIncomeEur))
        assertEquals(FirePlanner.NOTE, drip.note)
        assertNull(DividendReinvestment.project(listOf(from), BigDecimal.ONE, from))
    }

    @Test
    fun xirrAboveOneHundredPercentSurvivesNewtonFailure() {
        val start = LocalDate.of(2020, 1, 1)
        val rate = Xirr.annual(
            listOf(
                DatedCashflow(start, BigDecimal("-10")),
                DatedCashflow(start.plusDays(365), BigDecimal("100")),
                DatedCashflow(start.plusDays(730), BigDecimal("-150")),
                DatedCashflow(start.plusDays(1825), BigDecimal("100")),
            ),
        )
        assertNotNull(rate)
        assertTrue(rate!! > BigDecimal.ONE)
    }

    @Test
    fun dividendCadenceUsesCalendarMonths() {
        val from = LocalDate.of(2024, 8, 1)
        val quarterly = DividendCalendar.project(
            listOf(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 4, 1), LocalDate.of(2024, 7, 31)),
            BigDecimal.ONE,
            from,
        )
        assertEquals(com.pirlruc.finsilo.domain.performance.DividendCadence.QUARTERLY, quarterly!!.cadence)
    }

    @Test
    fun listingCountryUsesIsinWhenTheTickerHasNoSuffix() {
        assertEquals("US", ListingCountry.code("AAPL", "US0378331005"))
        assertEquals("DE", ListingCountry.code("VWCE.DE", "IE00B4L5Y983"))
        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("AAPL"))
        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("AAPL", "0378331005"))
    }

    @Test
    fun withholdingStaysUnknownUntilABrokerStatesIt() {
        val csv = """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),Withholding tax
            Market buy,2024-01-01 09:00:00,US0378331005,AAPL,Apple,2,100.00,EUR,200.00,EUR,
            Dividend,2024-06-01 09:00:00,US0378331005,AAPL,Apple,2,1.00,EUR,1.50,EUR,0.50
            Dividend,2024-09-01 09:00:00,US0378331005,AAPL,Apple,2,1.00,EUR,2.00,EUR,
        """.trimIndent()
        val parsed = BrokerCsv.parse(csv).lines
        val lines = parsed.filter { it.type == TransactionType.DIVIDEND }
        assertEquals(0, BigDecimal("0.50").compareTo(lines[0].withholdingEur))
        assertNull(lines[1].withholdingEur)
        val imported = ImportBrokerCsvUseCase().importLines(PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), parsed)
        assertEquals(emptyList<String>(), imported.skipped, imported.skipped.joinToString(" | "))
        val known = imported.snapshot.transactions.first { it.date == LocalDate.of(2024, 6, 1) }
        val unknown = imported.snapshot.transactions.first { it.date == LocalDate.of(2024, 9, 1) }
        assertEquals(0, BigDecimal("0.50").compareTo(known.withholdingEur))
        assertNull(unknown.withholdingEur)
        val rows = IrsAnnualReport().rows(imported.snapshot, 2024)
        val csvOut = IrsAnnualCsv.write(rows)
        assertTrue(csvOut.contains("unknown"))
        assertTrue(csvOut.contains("0.50") || csvOut.contains("0.5"))
        val api = """
            {"items":[{"ticker":"AAPL","amount":1.50,"quantity":2,"grossAmountPerShare":1.00,
            "paidOn":"2024-06-01T00:00:00Z","reference":"div-tax","currency":"EUR"}],"nextPagePath":null}
        """.trimIndent()
        val history = Trading212History.lines(emptyList(), listOf(api), emptyList()).single { it.type == TransactionType.DIVIDEND }
        assertEquals(0, BigDecimal("0.50").compareTo(history.withholdingEur))
    }

    @Test
    fun plannerVaultAndTaxEdges() {
        assertNull(FirePlanner.number(BigDecimal("10"), BigDecimal.ZERO))
        assertNull(FirePlanner.number(BigDecimal("10"), BigDecimal("2")))
        assertNull(FirePlanner.plan(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO))
        assertNull(FirePlanner.years(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE))
        assertNull(FirePlanner.years(BigDecimal.ONE, BigDecimal.TEN, BigDecimal("-1"), BigDecimal.ONE))
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(FirePlanner.years(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)))
        assertNull(FirePlanner.years(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO))
        val compounded = FirePlanner.years(BigDecimal("100000"), BigDecimal("1000000"), BigDecimal("20000"), BigDecimal("0.05"))
        assertNotNull(compounded)
        assertTrue(compounded!!.signum() > 0)
        assertNotNull(FirePlanner.years(BigDecimal("100"), BigDecimal("1000"), BigDecimal("50"), BigDecimal("-0.01")))
        assertNull(FirePlanner.years(BigDecimal("100"), BigDecimal("1000"), BigDecimal("5000"), BigDecimal("-2")))
        FirePlanner.years(BigDecimal("100"), BigDecimal("1000"), BigDecimal("2000"), BigDecimal("-1"))

        val even = mapOf("ETF" to BigDecimal("50.00"), "STOCK" to BigDecimal("50.00"))
        val half = mapOf("ETF" to BigDecimal("0.50"), "STOCK" to BigDecimal("0.50"))
        assertTrue(RebalanceSimulator.trades(even, half, buyOnly = false).isEmpty())
        assertTrue(RebalanceSimulator.trades(even, emptyMap(), false).isEmpty())
        val extra = RebalanceSimulator.trades(
            mapOf("ETF" to BigDecimal("40"), "CASH" to BigDecimal("10")),
            mapOf("ETF" to BigDecimal("100")),
            buyOnly = false,
            feeEur = BigDecimal("-1"),
        )
        assertTrue(extra.any { it.id == "CASH" && it.amountEur.signum() < 0 })
        val topped = RebalanceSimulator.trades(
            mapOf("ETF" to BigDecimal("10")),
            mapOf("ETF" to BigDecimal("0.5"), "NEW" to BigDecimal("0.5"), "ZERO" to BigDecimal.ZERO),
            buyOnly = true,
            feeEur = BigDecimal("100"),
        )
        assertTrue(topped.all { it.amountEur.signum() >= 0 })
        assertTrue(RebalanceSimulator.trades(even, mapOf("ETF" to BigDecimal.ZERO), buyOnly = true, feeEur = BigDecimal.ONE).isEmpty())

        val comments = VaultRegistry.decode("# note\n\n")
        assertEquals("default", comments.single().id)
        val injected = VaultRegistry.decode("bad\n|x|Nope\nab!|x|Nope\nfamily|ignored.db|\n")
        assertEquals("finsilo.db", injected.first { it.id == "default" }.fileName)
        assertEquals("family", injected.first { it.id == "family" }.label)
        assertNull(VaultRegistry.add(VaultRegistry.missing(), "---"))
        assertNull(VaultRegistry.add(VaultRegistry.missing(), "Default"))
        assertTrue(VaultRegistry.contains(injected, "family"))
        assertFalse(VaultRegistry.contains(injected, "missing"))
        assertEquals("default", VaultSelection().id)

        assertNull(WithholdingTax.grossMinusNet(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "USD"))
        assertNull(WithholdingTax.grossMinusNet(null, BigDecimal.ONE, BigDecimal.ONE, "EUR"))
        assertNull(WithholdingTax.grossMinusNet(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, "EUR"))
        val blankCurrency = """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total)
            Dividend,2024-06-01 09:00:00,US0378331005,AAPL,Apple,2,1.00,EUR,1.50,
        """.trimIndent()
        val derived = BrokerCsv.parse(blankCurrency).lines.single { it.type == TransactionType.DIVIDEND }
        assertEquals(0, BigDecimal("0.50").compareTo(derived.withholdingEur))

        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("AAPL", "12"))
        assertEquals(ListingCountry.UNKNOWN, ListingCountry.code("AAPL.ZZ"))
        val monthly = DividendCalendar.project(
            listOf(LocalDate.of(2023, 1, 15), LocalDate.of(2023, 2, 15), LocalDate.of(2023, 3, 15)),
            BigDecimal.ONE,
            LocalDate.of(2023, 3, 15),
        )
        assertEquals(com.pirlruc.finsilo.domain.performance.DividendCadence.MONTHLY, monthly!!.cadence)
        val start = LocalDate.of(2020, 1, 1)
        val doubled = Xirr.annual(listOf(DatedCashflow(start, BigDecimal("-100")), DatedCashflow(start.plusDays(365), BigDecimal("200"))))
        assertEquals(0, BigDecimal.ONE.compareTo(doubled))
        assertNull(Xirr.annual(listOf(DatedCashflow(start, BigDecimal("-1")))))
        val mild = Xirr.annual(
            listOf(
                DatedCashflow(start.plusDays(400), BigDecimal("110")),
                DatedCashflow(start, BigDecimal("-100")),
            ),
        )
        assertNotNull(mild)
        assertNull(FirePlanner.years(BigDecimal("100"), BigDecimal("1000"), BigDecimal("10"), BigDecimal("-0.5")))
        assertNull(FirePlanner.years(BigDecimal("-1000"), BigDecimal("100"), BigDecimal("10"), BigDecimal("0.05")))
        val penny = RebalanceSimulator.trades(
            mapOf("A" to BigDecimal("10"), "B" to BigDecimal("10")),
            mapOf("A" to BigDecimal("0.5"), "B" to BigDecimal("0.5"), "NEW" to BigDecimal.ZERO),
            buyOnly = false,
            feeEur = BigDecimal("0.01"),
        )
        val pennySum = penny.fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.amountEur) }
        assertEquals(0, BigDecimal("-0.01").compareTo(pennySum))
        assertNull(WithholdingTax.grossMinusNet(BigDecimal.ONE, null, BigDecimal.ONE, "EUR"))
        assertNull(WithholdingTax.grossMinusNet(BigDecimal.ONE, BigDecimal.ONE, null, "eur"))
        val restored = LedgerBackupParse.parse(
            listOf(
                "T\t1\ta\t2024-06-01\tDIVIDEND\t2\t1\t1\t1\t0\t1\tTRADING_212\t0.50",
                "T\t2\ta\t2024-09-01\tDIVIDEND\t2\t1\t1\t1\t0\t2\t\t",
                "T\t3\ta\t2024-10-01\tDIVIDEND\t2\t1\t1\t1\t0\t3\tNOT_A_SOURCE\t0.25",
                "T\t4\ta\t2024-11-01\tBUY\t1\t1\t1\t1\t0\t4",
            ),
            extrasEnabled = false,
        )
        val txs = (restored as LedgerBackupResult.Restored).snapshot.transactions
        assertEquals(0, BigDecimal("0.50").compareTo(txs.first { it.id == "1" }.withholdingEur))
        assertNull(txs.first { it.id == "2" }.withholdingEur)
        assertNull(txs.first { it.id == "3" }.source)
        assertNull(txs.first { it.id == "4" }.withholdingEur)
    }
}
