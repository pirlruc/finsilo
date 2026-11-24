package com.pirlruc.finsilo.domain.fundamentals

import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Optional OpenBB `equity/fundamental/metrics` and `ratios` documents. No live server is required. */
object OpenBbFundamentals {
    const val SOURCE: String = "OpenBB"

    private val json = Json { ignoreUnknownKeys = true }
    private val symbolPattern = Regex("^[A-Za-z0-9._-]{1,32}$")

    fun metricsUrl(base: String, symbol: String): String? = endpoint(base, "api/v1/equity/fundamental/metrics", symbol)

    fun ratiosUrl(base: String, symbol: String): String? = endpoint(base, "api/v1/equity/fundamental/ratios", symbol)

    fun host(base: String): String? {
        val uri = runCatching { URI(base.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null) return null
        return uri.host?.takeIf { it.isNotBlank() }
    }

    fun view(metricsBody: String, ratiosBody: String, price: BigDecimal?, asOf: LocalDate): FundamentalView {
        val metrics = firstRow(metricsBody)
        val ratios = firstRow(ratiosBody)
        if (metrics == null && ratios == null) return FundamentalView.missing()
        val inputs = inputs(metrics, ratios, price)
        val direct = direct(metrics, ratios)
        val computed = FundamentalsCalculator.metrics(inputs)
        return FundamentalView.ratios(merge(computed, direct), SOURCE, asOf)
    }

    /** Parses untrusted bytes. A broken document yields a missing view and does not throw. */
    fun parseLenient(metricsBody: String, ratiosBody: String): FundamentalView =
        runCatching { view(metricsBody, ratiosBody, null, LocalDate.EPOCH) }.getOrDefault(FundamentalView.missing())

    private fun inputs(metrics: JsonObject?, ratios: JsonObject?, price: BigDecimal?): FundamentalsInputs = FundamentalsInputs(
        price = price ?: number(metrics, "price") ?: number(ratios, "price"),
        ttmEps = number(metrics, "eps_ttm", "earnings_per_share", "eps") ?: number(ratios, "eps_ttm"),
        epsGrowthPercent = growth(number(metrics, "earnings_growth", "eps_growth") ?: number(ratios, "earnings_growth")),
        operatingCashFlowTtm = number(metrics, "operating_cash_flow"),
        capexTtm = spend(number(metrics, "capital_expenditure", "capex")),
        marketCap = number(metrics, "market_cap") ?: number(ratios, "market_cap"),
        ttmNetIncome = number(metrics, "net_income"),
        averageEquity = number(metrics, "average_equity"),
        ebitTtm = number(metrics, "ebit") ?: number(ratios, "ebit"),
        taxRate = fraction(number(metrics, "tax_rate") ?: number(ratios, "effective_tax_rate")),
        totalDebt = number(metrics, "total_debt") ?: number(ratios, "total_debt"),
        equity = number(metrics, "equity", "total_equity"),
        cash = number(metrics, "cash", "cash_and_equivalents"),
        interestExpense = spend(number(metrics, "interest_expense")),
    )

    private fun direct(metrics: JsonObject?, ratios: JsonObject?): ValuationMetrics = ValuationMetrics(
        pe = number(metrics, "pe_ratio") ?: number(ratios, "price_to_earnings_ratio", "pe_ratio"),
        peg = number(metrics, "peg_ratio") ?: number(ratios, "price_earnings_to_growth_ratio", "peg_ratio"),
        fcfYield = percent(number(metrics, "free_cash_flow_yield") ?: number(ratios, "free_cash_flow_yield")),
        roe = percent(number(metrics, "return_on_equity") ?: number(ratios, "return_on_equity")),
        roic = percent(number(metrics, "return_on_invested_capital") ?: number(ratios, "return_on_invested_capital")),
        debtToEquity = number(metrics, "debt_to_equity") ?: number(ratios, "debt_equity_ratio", "debt_to_equity"),
        evToEbit = number(metrics, "enterprise_value_to_ebit", "ev_to_ebit") ?: number(ratios, "enterprise_value_to_ebit"),
        interestCoverage = number(metrics, "interest_coverage") ?: number(ratios, "interest_coverage"),
    )

    private fun merge(computed: ValuationMetrics, direct: ValuationMetrics): ValuationMetrics = ValuationMetrics(
        pe = computed.pe ?: direct.pe,
        peg = computed.peg ?: direct.peg,
        fcfYield = computed.fcfYield ?: direct.fcfYield,
        roe = computed.roe ?: direct.roe,
        roic = computed.roic ?: direct.roic,
        debtToEquity = computed.debtToEquity ?: direct.debtToEquity,
        evToEbit = computed.evToEbit ?: direct.evToEbit,
        interestCoverage = computed.interestCoverage ?: direct.interestCoverage,
    )

    private fun endpoint(base: String, path: String, symbol: String): String? {
        if (!symbolPattern.matches(symbol) || host(base) == null) return null
        return base.trim().trimEnd('/') + "/" + path + "?symbol=" + symbol
    }

    private fun firstRow(body: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val results = root["results"] as? JsonArray ?: return null
        val first = results.firstOrNull() ?: return null
        return runCatching { first.jsonObject }.getOrNull()
    }

    private fun number(row: JsonObject?, vararg keys: String): BigDecimal? {
        if (row == null) return null
        return keys.firstNotNullOfOrNull { key ->
            val text = (row[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            text.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }?.toBigDecimalOrNull()
        }
    }

    private fun growth(raw: BigDecimal?): BigDecimal? = AlphaVantageFundamentals.growthPercent(raw)

    private fun fraction(raw: BigDecimal?): BigDecimal? {
        if (raw == null) return null
        return if (raw.abs() > BigDecimal.ONE) MoneyMath.div(raw, BigDecimal("100")) else raw
    }

    /** Yields and returns published as 0.15 become 15. A value already in percent is left alone. */
    private fun percent(raw: BigDecimal?): BigDecimal? {
        if (raw == null) return null
        return if (raw.abs() <= BigDecimal.ONE) raw.multiply(BigDecimal("100")) else raw
    }

    private fun spend(raw: BigDecimal?): BigDecimal? {
        if (raw == null) return null
        return if (raw.signum() < 0) raw.negate() else raw
    }
}
