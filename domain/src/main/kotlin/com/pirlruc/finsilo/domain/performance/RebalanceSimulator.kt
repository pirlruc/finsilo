package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.math.RoundingMode

/** One EUR trade. Positive is a buy. Negative is a sell. */
data class RebalanceTrade(val id: String, val amountEur: BigDecimal)

/**
 * Exact EUR trades that move holdings to target weights.
 * Amounts are rounded to cents. This is not financial advice.
 */
object RebalanceSimulator {
    /**
     * [weights] are fractions of 1, or percents when any weight is above 1.
     * [buyOnly] drops sells and tops up underweight names. [feeEur] is reserved cash.
     */
    fun trades(
        valuesEur: Map<String, BigDecimal>,
        weights: Map<String, BigDecimal>,
        buyOnly: Boolean,
        feeEur: BigDecimal = BigDecimal.ZERO,
    ): List<RebalanceTrade> {
        val fractions = fractions(weights)
        if (valuesEur.isEmpty() || fractions.isEmpty()) return emptyList()
        val drafted = if (buyOnly) buyOnly(valuesEur, fractions, feeEur) else full(valuesEur, fractions, feeEur)
        return drafted.filter { it.amountEur.signum() != 0 }
    }

    private fun full(valuesEur: Map<String, BigDecimal>, fractions: Map<String, BigDecimal>, feeEur: BigDecimal): List<RebalanceTrade> {
        val total = sum(valuesEur.values)
        val investable = total.subtract(cents(feeEur.max(BigDecimal.ZERO))).max(BigDecimal.ZERO)
        val ids = (valuesEur.keys + fractions.keys).distinct()
        val drafted = ids.map { id -> trade(id, valuesEur[id], investable, fractions[id]) }
        return balance(drafted, cents(investable.subtract(total)))
    }

    private fun trade(id: String, current: BigDecimal?, investable: BigDecimal, weight: BigDecimal?): RebalanceTrade {
        val target = investable.multiply(weight ?: BigDecimal.ZERO)
        return RebalanceTrade(id, cents(target.subtract(current ?: BigDecimal.ZERO)))
    }

    private fun buyOnly(valuesEur: Map<String, BigDecimal>, fractions: Map<String, BigDecimal>, feeEur: BigDecimal): List<RebalanceTrade> {
        val positive = fractions.filter { it.value.signum() > 0 }
        if (positive.isEmpty()) return emptyList()
        val future = positive.entries.fold(BigDecimal.ZERO) { acc, entry ->
            val needed = (valuesEur[entry.key] ?: BigDecimal.ZERO).divide(entry.value, SCALE, RoundingMode.HALF_UP)
            acc.max(needed)
        }
        val drafted = positive.map { (id, weight) ->
            val buy = cents(future.multiply(weight).subtract(valuesEur[id] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO)
            RebalanceTrade(id, buy)
        }
        return subtractFee(drafted, cents(feeEur.max(BigDecimal.ZERO)))
    }

    private fun subtractFee(trades: List<RebalanceTrade>, fee: BigDecimal): List<RebalanceTrade> {
        if (fee.signum() == 0 || trades.isEmpty()) return trades
        val index = trades.indices.maxBy { trades[it].amountEur }
        return trades.mapIndexed { i, trade ->
            if (i == index) trade.copy(amountEur = trade.amountEur.subtract(fee).max(BigDecimal.ZERO)) else trade
        }
    }

    private fun balance(trades: List<RebalanceTrade>, goal: BigDecimal): List<RebalanceTrade> {
        if (trades.isEmpty()) return trades
        val gap = cents(goal.subtract(sum(trades.map { it.amountEur })))
        if (gap.signum() == 0) return trades
        val index = trades.indices.maxBy { trades[it].amountEur.abs() }
        return trades.mapIndexed { i, trade ->
            if (i == index) trade.copy(amountEur = cents(trade.amountEur.add(gap))) else trade
        }
    }

    private fun fractions(weights: Map<String, BigDecimal>): Map<String, BigDecimal> {
        val peak = weights.values.maxOrNull() ?: return emptyMap()
        if (peak > BigDecimal.ONE) return weights.mapValues { it.value.divide(HUNDRED, SCALE, RoundingMode.HALF_UP) }
        return weights
    }

    private fun sum(values: Collection<BigDecimal>): BigDecimal = values.fold(BigDecimal.ZERO, BigDecimal::add)

    private fun cents(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal(100)
    private const val SCALE = 8
}
