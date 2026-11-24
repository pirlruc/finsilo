package com.pirlruc.finsilo.domain.fundamentals

import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Typed Alpha Vantage OVERVIEW plus the three statement documents. */
object AlphaVantageFundamentals {
    const val SOURCE: String = "Alpha Vantage"

    private val json = Json { ignoreUnknownKeys = true }

    fun view(overview: String, income: String, balance: String, cashFlow: String, price: BigDecimal?, asOf: LocalDate): FundamentalView {
        val inputs = inputs(overview, income, balance, cashFlow, price) ?: return FundamentalView.missing()
        return FundamentalView.ratios(FundamentalsCalculator.metrics(inputs), SOURCE, asOf)
    }

    fun inputs(overview: String, income: String, balance: String, cashFlow: String, price: BigDecimal?): FundamentalsInputs? {
        val profile = root(overview) ?: return null
        val incomeQuarter = reports(income, "quarterlyReports")
        val incomeYear = reports(income, "annualReports")
        val balanceYear = reports(balance, "annualReports")
        val cashQuarter = reports(cashFlow, "quarterlyReports")
        val cashYear = reports(cashFlow, "annualReports")
        return FundamentalsInputs(
            price = price,
            ttmEps = decimal(profile, "EPS"),
            epsGrowthPercent = growthPercent(decimal(profile, "QuarterlyEarningsGrowthYOY")),
            operatingCashFlowTtm = trailing(cashQuarter, cashYear, "operatingCashflow"),
            capexTtm = spend(trailing(cashQuarter, cashYear, "capitalExpenditures")),
            marketCap = decimal(profile, "MarketCapitalization"),
            ttmNetIncome = trailing(incomeQuarter, incomeYear, "netIncome"),
            averageEquity = averageEquity(balanceYear),
            ebitTtm = trailing(incomeQuarter, incomeYear, "ebit"),
            taxRate = taxRate(incomeQuarter, incomeYear),
            totalDebt = debt(balanceYear.firstOrNull()),
            equity = decimal(balanceYear.firstOrNull(), "totalShareholderEquity"),
            cash = decimal(balanceYear.firstOrNull(), "cashAndCashEquivalentsAtCarryingValue"),
            interestExpense = spend(trailing(incomeQuarter, incomeYear, "interestExpense")),
        )
    }

    private fun trailing(quarters: List<JsonObject>, years: List<JsonObject>, field: String): BigDecimal? {
        if (quarters.size >= TTM_QUARTERS) return sum(quarters.take(TTM_QUARTERS), field)
        return decimal(years.firstOrNull(), field)
    }

    private fun sum(rows: List<JsonObject>, field: String): BigDecimal? {
        val values = rows.map { decimal(it, field) }
        if (values.any { it == null }) return null
        return values.filterNotNull().fold(BigDecimal.ZERO) { acc, value -> MoneyMath.plus(acc, value) }
    }

    private fun averageEquity(years: List<JsonObject>): BigDecimal? {
        val current = decimal(years.getOrNull(0), "totalShareholderEquity") ?: return null
        val prior = decimal(years.getOrNull(1), "totalShareholderEquity") ?: return null
        return MoneyMath.div(MoneyMath.plus(current, prior), BigDecimal("2"))
    }

    private fun taxRate(quarters: List<JsonObject>, years: List<JsonObject>): BigDecimal? {
        val tax = trailing(quarters, years, "incomeTaxExpense") ?: return null
        val before = trailing(quarters, years, "incomeBeforeTax") ?: return null
        if (before.signum() <= 0) return null
        val rate = MoneyMath.div(tax, before)
        return if (rate.signum() < 0 || rate > BigDecimal.ONE) null else rate
    }

    private fun debt(row: JsonObject?): BigDecimal? {
        decimal(row, "shortLongTermDebtTotal")?.let { return it }
        val short = decimal(row, "shortTermDebt")
        val long = decimal(row, "longTermDebt")
        if (short == null || long == null) return null
        return MoneyMath.plus(short, long)
    }

    /** A reported cash outflow is often negative. The calculator subtracts a positive spend. */
    private fun spend(raw: BigDecimal?): BigDecimal? {
        if (raw == null) return null
        return if (raw.signum() < 0) raw.negate() else raw
    }

    /** A fraction such as 0.16 becomes 16. A figure already in percent is left alone. */
    fun growthPercent(raw: BigDecimal?): BigDecimal? {
        if (raw == null) return null
        return if (raw.abs() <= BigDecimal("2")) raw.multiply(BigDecimal("100")) else raw
    }

    private fun reports(body: String, name: String): List<JsonObject> {
        val value = root(body)?.get(name) as? JsonArray ?: return emptyList()
        return value.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }

    private fun root(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun decimal(row: JsonObject?, key: String): BigDecimal? {
        val primitive = row?.get(key) as? JsonPrimitive ?: return null
        val text = primitive.contentOrNull?.trim().orEmpty()
        if (text.isEmpty() || text.equals("None", ignoreCase = true) || text == "-") return null
        return text.toBigDecimalOrNull()
    }

    private const val TTM_QUARTERS: Int = 4
}
