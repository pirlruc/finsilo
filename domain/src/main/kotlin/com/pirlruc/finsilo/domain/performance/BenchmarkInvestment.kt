package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One close in the caller's price series. The series is not a promise of that return. */
data class PriceObservation(val date: LocalDate, val price: BigDecimal)

/**
 * Invests the same deposit and withdrawal cashflows in a caller-supplied price series.
 * The result is a comparison with that series, not a forecast.
 */
object BenchmarkInvestment {
    fun comparisonRate(flows: List<DatedCashflow>, prices: List<PriceObservation>, asOf: LocalDate): BigDecimal? {
        val terminal = terminalValue(flows, prices, asOf) ?: return null
        return Xirr.annual(flows + DatedCashflow(asOf, terminal))
    }

    fun terminalValue(flows: List<DatedCashflow>, prices: List<PriceObservation>, asOf: LocalDate): BigDecimal? {
        val ordered = prices.filter { it.price.signum() > 0 }.sortedBy { it.date }
        if (ordered.isEmpty()) return null
        val shares = sharesHeld(flows, ordered) ?: return null
        val end = priceOn(ordered, asOf) ?: return null
        return shares.multiply(end)
    }

    private fun sharesHeld(flows: List<DatedCashflow>, prices: List<PriceObservation>): BigDecimal? {
        var shares = BigDecimal.ZERO
        for (flow in flows.sortedBy { it.date }) {
            val price = priceOn(prices, flow.date) ?: return null
            shares = applyFlow(shares, flow.amountEur, price) ?: return null
        }
        return shares
    }

    private fun applyFlow(shares: BigDecimal, amount: BigDecimal, price: BigDecimal): BigDecimal? {
        val quantity = amount.abs().divide(price, SCALE, RoundingMode.HALF_UP)
        val next = if (amount.signum() < 0) shares.add(quantity) else shares.subtract(quantity)
        return next.takeIf { it.signum() >= 0 }
    }

    private fun priceOn(prices: List<PriceObservation>, date: LocalDate): BigDecimal? =
        prices.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.price

    private const val SCALE = 8
}
