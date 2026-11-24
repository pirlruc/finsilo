package com.pirlruc.finsilo.domain.fundamentals

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FundamentalsBranchTest {
    private val day = LocalDate.of(2026, 1, 2)

    @Test
    fun calculatorNullArmsStayNull() {
        val price = BigDecimal("10")
        val eps = BigDecimal("2")
        assertNull(FundamentalsCalculator.priceToEarnings(null, eps))
        assertNull(FundamentalsCalculator.priceToEarnings(price, null))
        assertNull(FundamentalsCalculator.priceToEarnings(BigDecimal("-1"), eps))
        assertNull(FundamentalsCalculator.priceToEarnings(price, BigDecimal.ZERO))
        assertNull(FundamentalsCalculator.peg(null, BigDecimal.ONE))
        assertNull(FundamentalsCalculator.peg(price, null))
        assertNull(FundamentalsCalculator.peg(price, BigDecimal("-1")))
        assertNull(FundamentalsCalculator.fcfYield(null, BigDecimal.ONE, price))
        assertNull(FundamentalsCalculator.fcfYield(BigDecimal.ONE, null, price))
        assertNull(FundamentalsCalculator.fcfYield(BigDecimal.ONE, BigDecimal.ONE, null))
        assertNull(FundamentalsCalculator.fcfYield(BigDecimal.ONE, BigDecimal.ONE, BigDecimal("-1")))
        assertEquals(0, BigDecimal("-1").compareTo(FundamentalsCalculator.fcfYield(BigDecimal.ONE, BigDecimal("11"), price)))
        assertNull(FundamentalsCalculator.overPositiveCapital(null, price))
        assertNull(FundamentalsCalculator.overPositiveCapital(price, null))
        assertEquals(0, BigDecimal("-0.4").compareTo(FundamentalsCalculator.overPositiveCapital(BigDecimal("-4"), price)))
        assertNull(FundamentalsCalculator.interestCoverage(null, price))
        assertNull(FundamentalsCalculator.interestCoverage(price, null))
        assertNull(FundamentalsCalculator.interestCoverage(BigDecimal.ZERO, price))
        assertNull(FundamentalsCalculator.interestCoverage(price, BigDecimal("-1")))
        val base = FundamentalsInputs(price = price, ttmEps = eps, ebitTtm = price)
        assertNull(FundamentalsCalculator.roic(base))
        assertNull(FundamentalsCalculator.roic(base.copy(taxRate = BigDecimal("0.2"), totalDebt = BigDecimal.ONE, equity = BigDecimal.ONE, cash = BigDecimal.ONE, ebitTtm = BigDecimal.ZERO)))
        assertNull(FundamentalsCalculator.roic(base.copy(taxRate = BigDecimal("-0.1"), totalDebt = BigDecimal.ONE, equity = BigDecimal.ONE, cash = BigDecimal.ZERO, ebitTtm = price)))
        assertNull(FundamentalsCalculator.roic(base.copy(taxRate = BigDecimal("1.1"), totalDebt = BigDecimal.ONE, equity = BigDecimal.ONE, cash = BigDecimal.ZERO, ebitTtm = price)))
        assertNull(FundamentalsCalculator.roic(base.copy(taxRate = BigDecimal("0.2"), totalDebt = BigDecimal.ONE, equity = BigDecimal.ONE, cash = BigDecimal("3"), ebitTtm = price)))
        assertNull(FundamentalsCalculator.evToEbit(base))
        assertNull(FundamentalsCalculator.evToEbit(base.copy(marketCap = price, totalDebt = BigDecimal.ONE, cash = BigDecimal.ZERO, ebitTtm = BigDecimal.ZERO)))
        assertTrue(FundamentalsCalculator.evToEbit(base.copy(marketCap = price, totalDebt = BigDecimal.ONE, cash = BigDecimal.ZERO, ebitTtm = price))!!.signum() > 0)
    }

    @Test
    fun alphaVantageFallsBackAndRejectsMissingTokens() {
        val annual = AlphaVantageFundamentals.view(profile(), annualIncome(), oneYearBalance(), annualCash(), BigDecimal("20"), day)
        assertEquals(ValuationNote.RATIOS, annual.note)
        assertNull(annual.metrics.roe)
        val none = AlphaVantageFundamentals.view(
            """{"EPS":"None","QuarterlyEarningsGrowthYOY":"-","MarketCapitalization":"None"}""",
            """{"annualReports":[{"netIncome":"-","ebit":"None","interestExpense":"0","incomeTaxExpense":"1","incomeBeforeTax":"0"}]}""",
            """{"annualReports":[{"shortTermDebt":"1"}]}""",
            """{"annualReports":[{"operatingCashflow":"-","capitalExpenditures":"1"}]}""",
            null,
            day,
        )
        assertNull(none.metrics.pe)
        assertNull(none.metrics.fcfYield)
        assertNull(AlphaVantageFundamentals.inputs("[]", "{}", "{}", "{}", null))
        val brokenQuarter = AlphaVantageFundamentals.view(
            profile(),
            """{"quarterlyReports":[{"netIncome":"1"},{"netIncome":"1"},{"netIncome":"1"},{"netIncome":"None"}],"annualReports":[{"netIncome":"9","ebit":"9","interestExpense":"1","incomeTaxExpense":"-1","incomeBeforeTax":"1"}]}""",
            """{"annualReports":[1,{"totalShareholderEquity":"4","shortTermDebt":"1","longTermDebt":"1","cashAndCashEquivalentsAtCarryingValue":"1"},{"totalShareholderEquity":"2"}]}""",
            """{"quarterlyReports":"nope"}""",
            BigDecimal("8"),
            day,
        )
        assertEquals(ValuationNote.RATIOS, brokenQuarter.note)
        assertNull(AlphaVantageFundamentals.growthPercent(BigDecimal("-0.5"))?.let { if (it.signum() > 0) it else null })
    }

    @Test
    fun openBbCoversDirectFieldsAndUnsafeUrls() {
        assertNull(OpenBbFundamentals.host("https://"))
        assertNull(OpenBbFundamentals.host("not a url"))
        assertNull(OpenBbFundamentals.host("https://user@host"))
        assertNull(OpenBbFundamentals.metricsUrl("https://openbb.example", ""))
        assertNull(OpenBbFundamentals.metricsUrl("https://openbb.example", "A".repeat(40)))
        val onlyRatios = OpenBbFundamentals.view("{}", """{"results":[{"price":"10","eps_ttm":"2","earnings_growth":"0.2","pe_ratio":{"bad":1},"return_on_equity":"null","free_cash_flow_yield":" ","interest_coverage":"abc","tax_rate":"21","capex":"-3","market_cap":"100"}]}""", null, day)
        assertEquals(0, BigDecimal("5").compareTo(onlyRatios.metrics.pe))
        val empty = OpenBbFundamentals.view("""{"results":[]}""", """{"results":[1]}""", null, day)
        assertEquals(ValuationNote.MISSING, empty.note)
        val encoded = FundamentalCodec.encode(FundamentalView.notApplicable())
        val decoded = FundamentalCodec.decode(encoded)
        assertEquals(ValuationNote.NOT_APPLICABLE, decoded!!.note)
        assertNull(decoded.asOf)
        val badDate = FundamentalCodec.decode("v1\nRATIOS\nsrc\nnot-a-date\npe\npe=nope\n")
        assertEquals(ValuationNote.RATIOS, badDate!!.note)
        assertNull(badDate.asOf)
        assertNull(badDate.metrics.pe)
        assertNull(FundamentalCodec.decode("v1\nRATIOS\n"))
    }

    @Test
    fun alternateKeysAndSingleNullArmsAreDefined() {
        val price = BigDecimal("10")
        val filled = base(price).copy(taxRate = BigDecimal("0.2"), totalDebt = BigDecimal.ONE, equity = BigDecimal.ONE, cash = BigDecimal.ZERO)
        assertNull(FundamentalsCalculator.roic(filled.copy(ebitTtm = null)))
        assertNull(FundamentalsCalculator.roic(filled.copy(taxRate = null)))
        assertNull(FundamentalsCalculator.roic(filled.copy(totalDebt = null)))
        assertNull(FundamentalsCalculator.roic(filled.copy(equity = null)))
        assertNull(FundamentalsCalculator.roic(filled.copy(cash = null)))
        assertNull(FundamentalsCalculator.evToEbit(filled.copy(marketCap = null)))
        assertNull(FundamentalsCalculator.evToEbit(filled.copy(totalDebt = null)))
        assertNull(FundamentalsCalculator.evToEbit(filled.copy(cash = null)))
        assertNull(FundamentalsCalculator.evToEbit(filled.copy(ebitTtm = null)))
        val metrics = """
            {"results":[{"price":"9","eps_ttm":"3","earnings_growth":"4","operating_cash_flow":"8","capital_expenditure":"2","market_cap":"80","net_income":"7","average_equity":"20","ebit":"6","tax_rate":"0.2","total_debt":"5","equity":"20","cash":"1","interest_expense":"1","pe_ratio":"3","peg_ratio":"1","free_cash_flow_yield":"4","return_on_equity":"12","return_on_invested_capital":"9","debt_to_equity":"0.2","enterprise_value_to_ebit":"11","interest_coverage":"6"}]}
        """.trim()
        val fromMetrics = OpenBbFundamentals.view(metrics, "{}", price, day)
        assertTrue(fromMetrics.metrics.pe != null)
        val ratios = """
            {"results":[{"price":"9","eps_ttm":"3","earnings_growth":"0.4","market_cap":"80","ebit":"6","effective_tax_rate":"20","total_debt":"5","price_to_earnings_ratio":"3","price_earnings_to_growth_ratio":"1","free_cash_flow_yield":"0.04","return_on_equity":"0.12","return_on_invested_capital":"0.09","debt_equity_ratio":"0.2","enterprise_value_to_ebit":"11","interest_coverage":"6","earnings_per_share":"3","eps_growth":"4","total_equity":"20","cash_and_equivalents":"1","capex":"2"}]}
        """.trim()
        val fromRatios = OpenBbFundamentals.view("{}", ratios, null, day)
        assertTrue(fromRatios.metrics.pe != null)
        assertNull(OpenBbFundamentals.host("http://openbb.example"))
        assertEquals("openbb.example", OpenBbFundamentals.host("https://openbb.example"))
        val oddTax = AlphaVantageFundamentals.view(
            profile(),
            """{"annualReports":[{"netIncome":"1","ebit":"1","interestExpense":"1","incomeTaxExpense":"3","incomeBeforeTax":"1"}]}""",
            """{"annualReports":[{"totalShareholderEquity":"1","shortTermDebt":"None","longTermDebt":"1"}]}""",
            """{"annualReports":[1]}""",
            price,
            day,
        )
        assertNull(oddTax.metrics.roic)
        assertNull(FundamentalCodec.decode("nope\na\nb\nc"))
    }

    private fun base(price: BigDecimal) = FundamentalsInputs(price = price, ttmEps = BigDecimal("2"), ebitTtm = price, marketCap = price)

    private fun profile() = """{"EPS":"2","QuarterlyEarningsGrowthYOY":"12","MarketCapitalization":"40"}"""

    private fun annualIncome() = """
        {"annualReports":[{"netIncome":"4","ebit":"6","interestExpense":"1","incomeTaxExpense":"1","incomeBeforeTax":"5"}]}
    """.trim()

    private fun oneYearBalance() = """
        {"annualReports":[{"totalShareholderEquity":"8","shortLongTermDebtTotal":"2","cashAndCashEquivalentsAtCarryingValue":"1"}]}
    """.trim()

    private fun annualCash() = """
        {"annualReports":[{"operatingCashflow":"5","capitalExpenditures":"-1"}]}
    """.trim()
}
