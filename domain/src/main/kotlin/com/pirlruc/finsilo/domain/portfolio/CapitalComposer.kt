package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerCapital
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
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
        val byBroker = LinkedHashMap<BrokerSource, ArrayList<Transaction>>()
        for (tx in snapshot.transactions) {
            contributed = applyContributed(contributed, tx)
            if (isCashInterest(tx, assets[tx.assetId]?.assetType)) {
                interest = plus(interest, tx.notionalEur)
            }
            val source = tx.source ?: continue
            byBroker.getOrPut(source) { ArrayList() }.add(tx)
        }
        return CapitalTotals(
            contributedEur = contributed,
            cashInterestEur = interest,
            totalGainEur = minus(total, contributed),
            brokers = byBroker.map { (source, rows) -> slice(source, rows, assets, holdings) },
        )
    }

    private fun applyContributed(current: BigDecimal, tx: Transaction): BigDecimal = when (tx.type) {
        TransactionType.DEPOSIT_CASH -> plus(current, tx.notionalEur)
        TransactionType.WITHDRAWAL -> minus(current, tx.notionalEur)
        else -> current
    }

    private fun slice(
        source: BrokerSource,
        rows: List<Transaction>,
        assets: Map<String, Asset>,
        holdings: List<HoldingValuation>,
    ): BrokerCapital {
        val contributed = rows.fold(ZERO) { acc, tx -> applyContributed(acc, tx) }
        val interest = rows.fold(ZERO) { acc, tx ->
            if (isCashInterest(tx, assets[tx.assetId]?.assetType)) plus(acc, tx.notionalEur) else acc
        }
        val cash = PositionLedger().cashEur(rows, assets)
        val invested = investedValue(rows, assets, holdings)
        return BrokerCapital(
            source = source,
            contributedEur = contributed,
            cashInterestEur = interest,
            cashEur = cash,
            gainEur = minus(plus(cash, invested), contributed),
        )
    }

    private fun investedValue(rows: List<Transaction>, assets: Map<String, Asset>, holdings: List<HoldingValuation>): BigDecimal {
        val ledger = PositionLedger()
        val unitByAsset =
            holdings.mapNotNull { holding ->
                unitPrice(holding)?.let { holding.asset.id to it }
            }.toMap()
        var value = ZERO
        for ((assetId, assetRows) in rows.groupBy { it.assetId }) {
            value = plus(value, markedValue(ledger, assets[assetId], assetRows, unitByAsset[assetId]))
        }
        return value
    }

    private fun markedValue(ledger: PositionLedger, asset: Asset?, rows: List<Transaction>, unitPrice: BigDecimal?): BigDecimal {
        if (asset == null || asset.assetType == AssetType.CASH) return ZERO
        return assetValue(ledger, asset, rows, unitPrice)
    }

    private fun assetValue(ledger: PositionLedger, asset: Asset, rows: List<Transaction>, unitPrice: BigDecimal?): BigDecimal {
        if (asset.locallyValued) return ledger.locallyValuedEur(rows)
        val position = ledger.position(rows)
        if (position.quantity.signum() <= 0) return ZERO
        val unit = unitPrice ?: return position.remainingCostEur
        return times(position.quantity, unit)
    }

    private fun unitPrice(holding: HoldingValuation): BigDecimal? {
        holding.priceEur?.let { return it }
        if (holding.quantity.signum() == 0) return null
        return div(holding.valueEur, holding.quantity)
    }

    private fun isCashInterest(tx: Transaction, assetType: AssetType?): Boolean =
        tx.type == TransactionType.INTEREST && assetType == AssetType.CASH
}
