package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerCapital
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import java.math.BigDecimal

/** Contributed capital vs cash-sweep interest, optionally split by import broker. */
internal data class CapitalTotals(
    val contributedEur: BigDecimal,
    val cashInterestEur: BigDecimal,
    val totalGainEur: BigDecimal,
    val brokers: List<BrokerCapital>,
)

internal object CapitalComposer {
    fun compose(snapshot: PortfolioSnapshot, holdings: List<HoldingValuation>, cash: BigDecimal): CapitalTotals {
        val assets = snapshot.assets.associateBy { it.id }
        val total = plus(holdings.fold(ZERO) { acc, holding -> plus(acc, holding.valueEur) }, cash)
        var contributed = ZERO
        var interest = ZERO
        for (tx in snapshot.transactions) {
            contributed = applyContributed(contributed, tx)
            if (isCashInterest(tx, assets[tx.assetId]?.assetType)) {
                interest = plus(interest, tx.notionalEur)
            }
        }
        return CapitalTotals(
            contributedEur = contributed,
            cashInterestEur = interest,
            totalGainEur = minus(total, contributed),
            brokers = CapitalAttributor.slices(snapshot, holdings),
        )
    }

    private fun applyContributed(current: BigDecimal, tx: Transaction): BigDecimal = when (tx.type) {
        TransactionType.DEPOSIT_CASH -> plus(current, tx.notionalEur)
        TransactionType.WITHDRAWAL -> minus(current, tx.notionalEur)
        else -> current
    }

    private fun isCashInterest(tx: Transaction, assetType: AssetType?): Boolean =
        tx.type == TransactionType.INTEREST && assetType == AssetType.CASH
}
