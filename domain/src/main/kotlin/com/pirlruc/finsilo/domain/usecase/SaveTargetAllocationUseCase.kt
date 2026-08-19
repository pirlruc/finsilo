package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.HUNDRED
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import java.math.BigDecimal
import java.math.RoundingMode

/** Result of saving target pie weights. */
sealed interface TargetAllocationResult {
    /** Weights accepted and ready to persist. */
    data class Accepted(val targets: List<TargetAllocation>) : TargetAllocationResult

    /** Why the weights were refused. */
    data class Rejected(val reason: String) : TargetAllocationResult
}

/** Persists validated target weights when they sum to 100. */
class SaveTargetAllocationUseCase {
    operator fun invoke(weights: Map<AssetType, BigDecimal>): TargetAllocationResult {
        for ((type, weight) in weights) {
            if (weight.signum() < 0) {
                return TargetAllocationResult.Rejected("${type.name} weight cannot be negative.")
            }
        }
        val complete = AssetType.entries.associateWith { weights[it] ?: ZERO }
        val sum = complete.values.fold(ZERO) { acc, value -> MoneyMath.plus(acc, value) }
        if (sum.setScale(2, RoundingMode.HALF_EVEN).compareTo(HUNDRED) != 0) {
            return TargetAllocationResult.Rejected(
                "Target weights must sum to 100 (currently ${sum.stripTrailingZeros().toPlainString()}).",
            )
        }
        val targets =
            complete
                .filter { it.value.signum() > 0 }
                .map { (type, weight) -> TargetAllocation(type, weight) }
        return TargetAllocationResult.Accepted(targets)
    }
}
