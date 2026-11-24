package com.pirlruc.finsilo.domain.fundamentals

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FundamentalsTest {
    private val asOf = LocalDate.of(2026, 11, 1)

    @Test
    fun ratiosAreNullWhenEarningsGrowthOrCapitalAreMissing() {
        val full = FundamentalsCalculator.metrics(sampleInputs())
        assertEquals(0, BigDecimal("20").compareTo(full.pe))
        assertEquals(0, BigDecimal("2").compareTo(full.peg))
        assertEquals(0, BigDecimal("0.05").compareTo(full.fcfYield))
        assertEquals(0, BigDecimal("0.5").compareTo(full.roe))
        assertTrue(full.roic!!.signum() > 0)
        assertEquals(0, BigDecimal("0.5").compareTo(full.debtToEquity))
        assertTrue(full.evToEbit!!.signum() > 0)
        assertEquals(0, BigDecimal("10").compareTo(full.interestCoverage))
        val negative = sampleInputs().copy(ttmEps = BigDecimal("-1"), ebitTtm = BigDecimal("-1"), epsGrowthPercent = null)
        val missing = FundamentalsCalculator.metrics(negative)
        assertNull(missing.pe)
        assertNull(missing.peg)
        assertNull(missing.evToEbit)
        assertNull(missing.interestCoverage)
        assertNull(FundamentalsCalculator.priceToEarnings(BigDecimal("-1"), BigDecimal("1")))
        assertNull(FundamentalsCalculator.peg(BigDecimal("10"), BigDecimal.ZERO))
        assertNull(FundamentalsCalculator.fcfYield(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO))
        assertNull(FundamentalsCalculator.overPositiveCapital(BigDecimal.ONE, BigDecimal.ZERO))
        assertNull(FundamentalsCalculator.overPositiveCapital(null, BigDecimal.ONE))
        assertNull(FundamentalsCalculator.interestCoverage(BigDecimal.ONE, BigDecimal.ZERO))
        val noCapital = sampleInputs().copy(totalDebt = null, equity = BigDecimal.ZERO, cash = null, taxRate = BigDecimal("2"))
        assertNull(FundamentalsCalculator.roic(noCapital))
        assertNull(FundamentalsCalculator.evToEbit(noCapital))
    }

    @Test
    fun alphaVantageStatementsUseTrailingQuartersAndStayDecimal() {
        val view = AlphaVantageFundamentals.view(overview(), income(), balance(), cashFlow(), BigDecimal("100"), asOf)
        assertEquals(ValuationNote.RATIOS, view.note)
        assertEquals(AlphaVantageFundamentals.SOURCE, view.source)
        assertEquals(asOf, view.asOf)
        assertEquals(0, BigDecimal("20").compareTo(view.metrics.pe))
        assertNull(AlphaVantageFundamentals.view("not-json", "{}", "{}", "{}", null, asOf).metrics.pe)
        assertEquals(ValuationNote.MISSING, AlphaVantageFundamentals.view("{", "{}", "{}", "{}", null, asOf).note)
        assertEquals(0, BigDecimal("16").compareTo(AlphaVantageFundamentals.growthPercent(BigDecimal("0.16"))))
        assertEquals(0, BigDecimal("15").compareTo(AlphaVantageFundamentals.growthPercent(BigDecimal("15"))))
        assertNull(AlphaVantageFundamentals.growthPercent(null))
    }

    @Test
    fun openBbMapsMetricsAndRatiosAndRejectsCleartext() {
        val metrics = """{"results":[{"pe_ratio":"28.5","return_on_equity":"0.15","free_cash_flow_yield":"3.5","debt_to_equity":"0.4"}]}"""
        val ratios = """{"results":[{"interest_coverage":"12","enterprise_value_to_ebit":"18.2","peg_ratio":"1.4"}]}"""
        val view = OpenBbFundamentals.view(metrics, ratios, BigDecimal("10"), asOf)
        assertEquals(OpenBbFundamentals.SOURCE, view.source)
        assertEquals(0, BigDecimal("28.5").compareTo(view.metrics.pe))
        assertEquals(0, BigDecimal("15").compareTo(view.metrics.roe))
        assertEquals(0, BigDecimal("3.5").compareTo(view.metrics.fcfYield))
        assertEquals(0, BigDecimal("12").compareTo(view.metrics.interestCoverage))
        assertNull(OpenBbFundamentals.metricsUrl("http://openbb.example", "AAPL"))
        assertEquals("openbb.example", OpenBbFundamentals.host("https://openbb.example/api"))
        assertTrue(OpenBbFundamentals.metricsUrl("https://openbb.example", "AAPL")!!.startsWith("https://openbb.example/"))
        assertNull(OpenBbFundamentals.ratiosUrl("https://user:pw@openbb.example", "AAPL"))
        assertNull(OpenBbFundamentals.metricsUrl("https://openbb.example", "../AAPL"))
        assertEquals(ValuationNote.MISSING, OpenBbFundamentals.parseLenient("{", "").note)
        assertEquals(ValuationNote.MISSING, OpenBbFundamentals.view("{}", "{}", null, asOf).note)
        val encoded = FundamentalCodec.encode(view)
        val decoded = FundamentalCodec.decode(encoded)
        assertEquals(0, view.metrics.pe!!.compareTo(decoded!!.metrics.pe))
        assertNull(FundamentalCodec.decode("nope"))
        assertNull(FundamentalCodec.decode("v1\nNOPE\n\n\n"))
    }

    private fun sampleInputs() = FundamentalsInputs(
        price = BigDecimal("100"),
        ttmEps = BigDecimal("5"),
        epsGrowthPercent = BigDecimal("10"),
        operatingCashFlowTtm = BigDecimal("30"),
        capexTtm = BigDecimal("10"),
        marketCap = BigDecimal("400"),
        ttmNetIncome = BigDecimal("40"),
        averageEquity = BigDecimal("80"),
        ebitTtm = BigDecimal("50"),
        taxRate = BigDecimal("0.20"),
        totalDebt = BigDecimal("40"),
        equity = BigDecimal("80"),
        cash = BigDecimal("10"),
        interestExpense = BigDecimal("5"),
    )

    private fun overview() = """
        {"EPS":"5","QuarterlyEarningsGrowthYOY":"0.10","MarketCapitalization":"400","NoneField":"None"}
    """.trim()

    private fun income() = """
        {"quarterlyReports":[
          {"netIncome":"10","ebit":"12","interestExpense":"-1","incomeTaxExpense":"2","incomeBeforeTax":"10"},
          {"netIncome":"10","ebit":"12","interestExpense":"-1","incomeTaxExpense":"2","incomeBeforeTax":"10"},
          {"netIncome":"10","ebit":"13","interestExpense":"-2","incomeTaxExpense":"2","incomeBeforeTax":"10"},
          {"netIncome":"10","ebit":"13","interestExpense":"-1","incomeTaxExpense":"2","incomeBeforeTax":"10"}
        ],"annualReports":[{"netIncome":"1","ebit":"1","interestExpense":"1","incomeTaxExpense":"1","incomeBeforeTax":"1"}]}
    """.trim()

    private fun balance() = """
        {"annualReports":[
          {"totalShareholderEquity":"90","shortTermDebt":"10","longTermDebt":"30","cashAndCashEquivalentsAtCarryingValue":"10"},
          {"totalShareholderEquity":"70","shortLongTermDebtTotal":"20","cashAndCashEquivalentsAtCarryingValue":"5"}
        ]}
    """.trim()

    private fun cashFlow() = """
        {"quarterlyReports":[
          {"operatingCashflow":"8","capitalExpenditures":"-2"},
          {"operatingCashflow":"8","capitalExpenditures":"-3"},
          {"operatingCashflow":"7","capitalExpenditures":"-2"},
          {"operatingCashflow":"7","capitalExpenditures":"-3"}
        ]}
    """.trim()
}
