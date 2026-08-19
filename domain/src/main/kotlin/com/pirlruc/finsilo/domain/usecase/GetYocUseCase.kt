package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.YocReport
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.HUNDRED
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Yield on cost for holdings that pay dividends.
 *
 * - TTM: sum of dividend EUR in the last 365 days / FIFO remaining cost.
 * - Last × frequency: last cash dividend × inferred payments-per-year /
 *   FIFO remaining cost. Frequency is inferred from how many dividend
 *   payments landed in the TTM window (1, 2, 4, or 12).
 */
class GetYocUseCase(private val ledger: PositionLedger = PositionLedger()) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): List<YocReport> {
        val ttmStart = asOf.minusDays(365)
        val txsByAsset = ledger.transactionsOnOrBefore(snapshot.transactions, asOf).groupBy { it.assetId }
        return snapshot.assets.mapNotNull { asset ->
            if (asset.assetType == AssetType.CASH || asset.locallyValued) return@mapNotNull null
            val txs = txsByAsset[asset.id].orEmpty()
            val lots = ledger.position(txs)
            if (lots.quantity.signum() <= 0 || lots.remainingCostEur.signum() <= 0) return@mapNotNull null
            val dividends = txs.filter { it.type == TransactionType.DIVIDEND }
            if (dividends.isEmpty()) return@mapNotNull null
            val ttm = dividends.filter { !it.date.isBefore(ttmStart) }
            val ttmSum = ttm.fold(ZERO) { acc, tx -> acc.add(tx.notionalEur) }
            val ttmPercent = if (ttm.isEmpty()) null else times(div(ttmSum, lots.remainingCostEur), HUNDRED)
            val last = dividends.maxBy { it.date }
            val paymentsPerYear = inferPaymentsPerYear(ttm.size)
            val lastTimesFreq =
                paymentsPerYear?.let { freq ->
                    val annualized = times(last.notionalEur, BigDecimal(freq))
                    times(div(annualized, lots.remainingCostEur), HUNDRED)
                }
            YocReport(
                asset = asset,
                remainingCostEur = lots.remainingCostEur,
                ttmPercent = ttmPercent,
                lastTimesFrequencyPercent = lastTimesFreq,
                paymentsPerYear = paymentsPerYear,
            )
        }
    }

    companion object {
        fun inferPaymentsPerYear(paymentsInTtm: Int): Int? = when {
            paymentsInTtm <= 0 -> null
            paymentsInTtm == 1 -> 1
            paymentsInTtm == 2 -> 2
            paymentsInTtm in 3..5 -> 4
            else -> 12
        }
    }
}
