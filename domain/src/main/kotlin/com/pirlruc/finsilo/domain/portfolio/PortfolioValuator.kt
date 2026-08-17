package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.percentOf
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioValuator(private val ledger: PositionLedger = PositionLedger()) {
    private val navCache = HashMap<String, BigDecimal>()

    fun valueHoldings(snapshot: PortfolioSnapshot, asOf: LocalDate): List<HoldingValuation> {
        val txsByAsset = ledger.transactionsOnOrBefore(snapshot.transactions, asOf).groupBy { it.assetId }
        val marketByAsset = ledger.indexMarket(snapshot.marketData)
        val eurPerUsd = ledger.eurPerUsdOn(asOf, snapshot.fxRates)

        return snapshot.assets.mapNotNull { asset ->
            val txs = txsByAsset[asset.id].orEmpty()
            if (txs.isEmpty()) return@mapNotNull null
            if (asset.locallyValued) {
                val value = ledger.locallyValuedEur(txs)
                if (value.signum() == 0) return@mapNotNull null
                val cost = costOfLocalInstrument(txs)
                HoldingValuation(
                    asset = asset,
                    quantity = BigDecimal.ONE,
                    priceEur = value,
                    valueEur = value,
                    costEur = cost,
                    unrealizedPnlEur = minus(value, cost),
                )
            } else {
                val lot = ledger.position(txs)
                if (lot.quantity.signum() == 0) return@mapNotNull null
                val native = ledger.nativePrice(asset.id, asOf, marketByAsset, txs) ?: return@mapNotNull null
                val rate = priceRate(asset, eurPerUsd) ?: return@mapNotNull null
                val priceEur = toEur(native, asset.baseCurrency, rate)
                val value = times(lot.quantity, priceEur)
                HoldingValuation(
                    asset = asset,
                    quantity = lot.quantity,
                    priceEur = priceEur,
                    valueEur = value,
                    costEur = lot.remainingCostEur,
                    unrealizedPnlEur = minus(value, lot.remainingCostEur),
                )
            }
        }
    }

    fun missingUsdFx(snapshot: PortfolioSnapshot): Boolean = snapshot.fxRates.isEmpty() &&
        snapshot.assets.any { it.baseCurrency == Currency.USD && !it.locallyValued }

    private fun priceRate(asset: Asset, eurPerUsd: BigDecimal?): BigDecimal? =
        if (asset.baseCurrency == Currency.EUR) BigDecimal.ONE else eurPerUsd

    fun cashEur(snapshot: PortfolioSnapshot, asOf: LocalDate): BigDecimal {
        val assetsById = snapshot.assets.associateBy { it.id }
        val txs = ledger.transactionsOnOrBefore(snapshot.transactions, asOf)
        return ledger.cashEur(txs, assetsById)
    }

    fun totalNavEur(snapshot: PortfolioSnapshot, asOf: LocalDate): BigDecimal {
        val key = navKey(snapshot, asOf)
        return navCache.getOrPut(key) {
            val holdings = valueHoldings(snapshot, asOf)
            val cash = cashEur(snapshot, asOf)
            holdings.fold(cash) { acc, holding -> plus(acc, holding.valueEur) }
        }
    }

    private fun navKey(snapshot: PortfolioSnapshot, asOf: LocalDate): String {
        val lastTx = snapshot.transactions.lastOrNull()?.id ?: "-"
        return "$asOf|${snapshot.transactions.size}|$lastTx|${snapshot.fxRates.size}|${snapshot.marketData.size}"
    }

    fun allocation(snapshot: PortfolioSnapshot, asOf: LocalDate): AllocationReport {
        val holdings = valueHoldings(snapshot, asOf)
        val cash = cashEur(snapshot, asOf)
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

    private fun costOfLocalInstrument(transactions: List<com.pirlruc.finsilo.domain.model.Transaction>): BigDecimal {
        var cost = ZERO
        for (tx in transactions) {
            cost =
                when (tx.type) {
                    com.pirlruc.finsilo.domain.model.TransactionType.BUY ->
                        plus(cost, plus(tx.notionalEur, tx.feesEur))
                    com.pirlruc.finsilo.domain.model.TransactionType.SELL -> {
                        // Withdrawals reduce remaining principal cost, not below zero.
                        val reduced = minus(cost, tx.notionalEur)
                        if (reduced.signum() < 0) ZERO else reduced
                    }
                    else -> cost
                }
        }
        return cost
    }

    fun syntheticCashAsset(): Asset = Asset(
        id = CASH_ASSET_ID,
        symbol = "EUR",
        name = "Cash",
        assetType = AssetType.CASH,
        baseCurrency = com.pirlruc.finsilo.domain.model.Currency.EUR,
    )

    companion object {
        const val CASH_ASSET_ID: String = "cash"
        val DRIFT_BAND_PERCENT: BigDecimal = BigDecimal("5")
    }
}
