package com.pirlruc.finsilo.domain.performance

import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.HoldingValuation
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rebalance inputs keyed by instrument. A type-level target is split across
 * the holdings of that type in proportion to their current EUR value.
 */
object RebalanceBook {
    fun valuesAndTargets(
        holdings: List<HoldingValuation>,
        slices: List<AllocationSlice>,
    ): Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> {
        if (holdings.isEmpty()) return typeBook(slices)
        val values = holdings.associate { it.asset.symbol to it.valueEur }
        val targets = linkedMapOf<String, BigDecimal>()
        for (slice in slices) {
            val target = slice.targetPercent ?: continue
            split(holdings.filter { it.asset.assetType == slice.assetType }, target, targets)
        }
        return values to targets
    }

    private fun typeBook(slices: List<AllocationSlice>): Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> {
        val values = slices.associate { it.assetType.name to it.valueEur }
        val targets = slices.mapNotNull { slice -> slice.targetPercent?.let { slice.assetType.name to it } }.toMap()
        return values to targets
    }

    private fun split(group: List<HoldingValuation>, target: BigDecimal, targets: MutableMap<String, BigDecimal>) {
        val total = group.fold(BigDecimal.ZERO) { acc, holding -> acc.add(holding.valueEur) }
        if (group.isEmpty() || total.signum() == 0) return
        group.forEach { holding ->
            val share = holding.valueEur.divide(total, SCALE, RoundingMode.HALF_UP)
            targets[holding.asset.symbol] = target.multiply(share)
        }
    }

    private const val SCALE: Int = 8
}
