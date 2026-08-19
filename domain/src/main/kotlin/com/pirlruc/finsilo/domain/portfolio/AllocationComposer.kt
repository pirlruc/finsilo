package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.percentOf
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import java.math.BigDecimal
import java.time.LocalDate

internal object AllocationComposer {
    fun compose(snapshot: PortfolioSnapshot, asOf: LocalDate, holdings: List<HoldingValuation>, cash: BigDecimal): AllocationReport {
        val holdingTotal = holdings.fold(ZERO) { acc, h -> plus(acc, h.valueEur) }
        val total = plus(holdingTotal, cash)
        val targets = snapshot.targets.associate { it.assetType to it.weightPercent }
        val byType = LinkedHashMap<AssetType, BigDecimal>()
        for (holding in holdings) {
            val type = holding.asset.assetType
            byType[type] = plus(byType[type] ?: ZERO, holding.valueEur)
        }
        if (cash.signum() > 0) {
            byType[AssetType.CASH] = plus(byType[AssetType.CASH] ?: ZERO, cash)
        }
        val slices =
            byType.entries
                .sortedByDescending { it.value }
                .map { (type, value) ->
                    val weight = percentOf(value, total)
                    val target = targets[type]
                    AllocationSlice(
                        assetType = type,
                        valueEur = value,
                        weightPercent = weight,
                        targetPercent = target,
                        driftPercent = target?.let { minus(weight, it) },
                    )
                }
        val unrealized = holdings.fold(ZERO) { acc, h -> plus(acc, h.unrealizedPnlEur) }
        return AllocationReport(
            asOf = asOf,
            totalValueEur = total,
            unrealizedPnlEur = unrealized,
            cashEur = cash,
            slices = slices,
            holdings = holdings.sortedByDescending { it.valueEur },
        )
    }
}
