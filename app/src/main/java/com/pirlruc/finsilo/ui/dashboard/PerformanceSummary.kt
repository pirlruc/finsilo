package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.BenchmarkInvestment
import com.pirlruc.finsilo.domain.performance.DatedCashflow
import com.pirlruc.finsilo.domain.performance.DividendCalendar
import com.pirlruc.finsilo.domain.performance.IrsAnnualReport
import com.pirlruc.finsilo.domain.performance.PriceObservation
import com.pirlruc.finsilo.domain.performance.Xirr
import com.pirlruc.finsilo.ui.formatEur
import com.pirlruc.finsilo.ui.formatPercent
import java.math.BigDecimal
import java.time.LocalDate

/** Short labels for Overview and Insights. Benchmark text is a comparison, not a promise. */
data class PerformanceSummary(val xirr: String, val benchmark: String, val dividends: String, val taxNote: String)

internal object PerformanceLabels {
    fun of(snapshot: PortfolioSnapshot, navEur: BigDecimal, asOf: LocalDate): PerformanceSummary {
        val flows = externalFlows(snapshot)
        val rate = Xirr.annual(flows + DatedCashflow(asOf, navEur))
        return PerformanceSummary(
            xirr = rate?.let { "XIRR ${percent(it)}" } ?: "XIRR unavailable",
            benchmark = benchmark(flows, snapshot, asOf),
            dividends = dividends(snapshot, asOf),
            taxNote = tax(snapshot, asOf.year),
        )
    }

    private fun externalFlows(snapshot: PortfolioSnapshot): List<DatedCashflow> = snapshot.transactions.mapNotNull { tx ->
        when (tx.type) {
            TransactionType.DEPOSIT_CASH -> DatedCashflow(tx.date, tx.notionalEur.negate())
            TransactionType.WITHDRAWAL -> DatedCashflow(tx.date, tx.notionalEur)
            else -> null
        }
    }

    private fun benchmark(flows: List<DatedCashflow>, snapshot: PortfolioSnapshot, asOf: LocalDate): String {
        val parts = listOf("VWCE.DE" to "VWCE", "SPY" to "S&P 500").mapNotNull { (symbol, label) ->
            rate(flows, prices(snapshot, symbol), asOf)?.let { "$label ${percent(it)}" }
        }
        if (parts.isEmpty()) return "No stored VWCE or S&P 500 history"
        return parts.joinToString(" · ") + ". Comparison, not a promised return"
    }

    private fun rate(flows: List<DatedCashflow>, prices: List<PriceObservation>, asOf: LocalDate): BigDecimal? =
        if (prices.isEmpty()) null else BenchmarkInvestment.comparisonRate(flows, prices, asOf)

    private fun prices(snapshot: PortfolioSnapshot, symbol: String): List<PriceObservation> {
        val ids = snapshot.assets.filter { it.feedSymbol.equals(symbol, true) || it.symbol.equals(symbol, true) }.map { it.id }
        return snapshot.marketData.filter { it.assetId in ids }.map { PriceObservation(it.date, it.closingPriceNative) }
    }

    private fun dividends(snapshot: PortfolioSnapshot, asOf: LocalDate): String {
        val projections = snapshot.transactions.filter { it.type == TransactionType.DIVIDEND }.groupBy { it.assetId }
            .values.mapNotNull { rows ->
                DividendCalendar.project(rows.map { it.date }, rows.maxBy { it.date }.notionalEur, asOf)
            }
        if (projections.isEmpty()) return "No regular dividend date"
        val next = projections.minBy { it.nextDate }
        val income = projections.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.forwardIncomeEur) }
        return "${next.cadence.name.lowercase()} · next ${next.nextDate} · forward ${formatEur(income)}"
    }

    private fun tax(snapshot: PortfolioSnapshot, year: Int): String {
        val count = IrsAnnualReport().rows(snapshot, year).size
        return "IRS $year: $count rows. Not tax advice and not the official XML."
    }

    private fun percent(rate: BigDecimal): String = formatPercent(rate.multiply(BigDecimal("100")))
}
