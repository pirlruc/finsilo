package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/** One dated EUR cashflow. Deposits are negative; withdrawals and a terminal value are positive. */
data class DatedCashflow(val date: LocalDate, val amountEur: BigDecimal)

/**
 * Annual internal rate of return. Newton-Raphson first, then bisection.
 * Returns null when the cashflows have no rate, instead of throwing.
 */
object Xirr {
    fun annual(flows: List<DatedCashflow>): BigDecimal? {
        val usable = flows.filter { it.amountEur.signum() != 0 }
        if (!solvable(usable)) return null
        return newton(usable) ?: bisect(usable)
    }

    private fun solvable(flows: List<DatedCashflow>): Boolean {
        if (flows.size < 2) return false
        val signs = flows.map { it.amountEur.signum() }.toSet()
        return signs.contains(-1) && signs.contains(1)
    }

    private fun newton(flows: List<DatedCashflow>): BigDecimal? {
        var rate = 0.1
        repeat(NEWTON_STEPS) {
            val step = newtonStep(flows, rate) ?: return null
            if (step.done) return decimal(step.rate)
            rate = step.rate
        }
        return null
    }

    private fun newtonStep(flows: List<DatedCashflow>, rate: Double): NewtonStep? {
        val value = npv(flows, rate)
        if (abs(value) < TOLERANCE) return NewtonStep(rate, done = true)
        val slope = derivative(flows, rate)
        if (abs(slope) < TOLERANCE) return null
        val next = rate - value / slope
        if (next <= FLOOR || next > CAP) return null
        return NewtonStep(next, done = false)
    }

    private fun bisect(flows: List<DatedCashflow>): BigDecimal? {
        var low = FLOOR
        var high = 1.0
        if (npv(flows, low) * npv(flows, high) > 0) return null
        repeat(BISECT_STEPS) {
            val mid = (low + high) / 2.0
            val value = npv(flows, mid)
            if (abs(value) < TOLERANCE) return decimal(mid)
            if (value * npv(flows, low) > 0) low = mid else high = mid
        }
        return decimal((low + high) / 2.0)
    }

    private fun npv(flows: List<DatedCashflow>, rate: Double): Double = flows.sumOf { flow ->
        flow.amountEur.toDouble() / (1.0 + rate).pow(years(flows, flow))
    }

    private fun derivative(flows: List<DatedCashflow>, rate: Double): Double = flows.sumOf { flow ->
        val span = years(flows, flow)
        if (span == 0.0) 0.0 else -span * flow.amountEur.toDouble() / (1.0 + rate).pow(span + 1.0)
    }

    private fun years(flows: List<DatedCashflow>, flow: DatedCashflow): Double {
        val start = flows.minOf { it.date }
        return ChronoUnit.DAYS.between(start, flow.date).toDouble() / YEAR_DAYS
    }

    private fun decimal(rate: Double): BigDecimal = BigDecimal.valueOf(rate).setScale(SCALE, RoundingMode.HALF_UP)

    private data class NewtonStep(val rate: Double, val done: Boolean)

    private const val TOLERANCE = 1e-9
    private const val FLOOR = -0.999
    private const val CAP = 100.0
    private const val YEAR_DAYS = 365.0
    private const val NEWTON_STEPS = 40
    private const val BISECT_STEPS = 80
    private const val SCALE = 8
}
