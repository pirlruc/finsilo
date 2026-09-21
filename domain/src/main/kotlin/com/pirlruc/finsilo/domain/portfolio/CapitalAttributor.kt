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
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.min
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import java.math.BigDecimal
import java.util.ArrayDeque

/** Per-broker leftover cash and lots from a global FIFO walk. */
internal object CapitalAttributor {
    fun slices(snapshot: PortfolioSnapshot, holdings: List<HoldingValuation>): List<BrokerCapital> {
        val walk = Walk(snapshot.assets.associateBy { it.id }, unitPrices(holdings))
        PositionLedger().ordered(snapshot.transactions).forEach { walk.apply(it) }
        return walk.finish()
    }

    private fun unitPrices(holdings: List<HoldingValuation>): Map<String, BigDecimal> = holdings.mapNotNull { holding ->
        val unit = holding.priceEur ?: holding.quantity.takeIf { it.signum() != 0 }?.let { div(holding.valueEur, it) }
        unit?.let { holding.asset.id to it }
    }.toMap()

    private class Walk(private val assets: Map<String, Asset>, private val unitByAsset: Map<String, BigDecimal>) {
        private val cashBy = LinkedHashMap<BrokerSource, BigDecimal>()
        private val contributedBy = LinkedHashMap<BrokerSource, BigDecimal>()
        private val interestBy = LinkedHashMap<BrokerSource, BigDecimal>()
        private val localBy = HashMap<Pair<BrokerSource, String>, BigDecimal>()
        private val lots = HashMap<String, ArrayDeque<SourceLot>>()

        fun apply(tx: Transaction) {
            when (tx.type) {
                TransactionType.DEPOSIT_CASH -> deposit(tx)
                TransactionType.WITHDRAWAL -> withdraw(tx)
                TransactionType.BUY -> buy(tx)
                TransactionType.SELL -> sell(tx)
                TransactionType.DIVIDEND, TransactionType.INTEREST -> income(tx)
            }
        }

        fun finish(): List<BrokerCapital> {
            val invested = investedBySource()
            val sources = (contributedBy.keys + cashBy.keys + interestBy.keys + invested.keys).toSet()
            return sources.sortedBy { it.name }.map { source ->
                val contributed = contributedBy[source] ?: ZERO
                val cash = cashBy[source] ?: ZERO
                BrokerCapital(
                    source = source,
                    contributedEur = contributed,
                    cashInterestEur = interestBy[source] ?: ZERO,
                    cashEur = cash,
                    gainEur = minus(plus(cash, invested[source] ?: ZERO), contributed),
                )
            }
        }

        private fun deposit(tx: Transaction) {
            creditCash(bucket(tx.source), tx.notionalEur)
            bumpContributed(bucket(tx.source), tx.notionalEur)
        }

        private fun withdraw(tx: Transaction) {
            spend(tx.source, tx.notionalEur)
            bumpContributed(bucket(tx.source), tx.notionalEur.negate())
        }

        private fun buy(tx: Transaction) {
            val cost = plus(tx.notionalEur, tx.feesEur)
            spend(tx.source, cost)
            val asset = assets[tx.assetId] ?: return
            if (asset.assetType == AssetType.CASH) return
            if (asset.locallyValued) {
                bumpLocal(bucket(tx.source), tx.assetId, cost)
                return
            }
            lots.getOrPut(tx.assetId) { ArrayDeque() }.addLast(SourceLot(bucket(tx.source), tx.quantity, cost))
        }

        private fun sell(tx: Transaction) {
            val asset = assets[tx.assetId]
            if (asset?.locallyValued == true) {
                creditCash(bucket(tx.source), minus(tx.notionalEur, tx.feesEur))
                bumpLocal(bucket(tx.source), tx.assetId, tx.notionalEur.negate())
                return
            }
            val filled = consume(tx.assetId, tx.quantity)
            creditCash(bucket(tx.source), minus(times(tx.unitPriceEur, filled), tx.feesEur))
        }

        private fun income(tx: Transaction) {
            val asset = assets[tx.assetId]
            val source = bucket(tx.source)
            if (tx.type == TransactionType.INTEREST && asset?.assetType == AssetType.CASH) {
                creditCash(source, tx.notionalEur)
                interestBy[source] = plus(interestBy[source] ?: ZERO, tx.notionalEur)
                return
            }
            if (asset?.locallyValued == true) {
                bumpLocal(source, tx.assetId, tx.notionalEur)
                return
            }
            creditCash(source, tx.notionalEur)
        }

        private fun bucket(source: BrokerSource?): BrokerSource = source ?: BrokerSource.MANUAL

        private fun creditCash(source: BrokerSource, amount: BigDecimal) {
            cashBy[source] = plus(cashBy[source] ?: ZERO, amount)
        }

        private fun bumpContributed(source: BrokerSource, amount: BigDecimal) {
            contributedBy[source] = plus(contributedBy[source] ?: ZERO, amount)
        }

        private fun bumpLocal(source: BrokerSource, assetId: String, amount: BigDecimal) {
            val key = source to assetId
            localBy[key] = plus(localBy[key] ?: ZERO, amount)
        }

        private fun spend(preferred: BrokerSource?, amount: BigDecimal) {
            val first = bucket(preferred)
            var left = take(first, amount)
            if (left.signum() <= 0) return
            cashBy.keys.filterNot { it == first }.forEach { source ->
                if (left.signum() > 0) left = take(source, left)
            }
        }

        private fun take(source: BrokerSource, amount: BigDecimal): BigDecimal {
            val have = cashBy[source] ?: return amount
            val used = min(have, amount)
            cashBy[source] = minus(have, used)
            return minus(amount, used)
        }

        private fun consume(assetId: String, sellQty: BigDecimal): BigDecimal {
            val queue = lots.getOrPut(assetId) { ArrayDeque() }
            var remaining = sellQty
            var filled = ZERO
            while (remaining.signum() > 0 && queue.isNotEmpty()) {
                val lot = queue.removeFirst()
                if (lot.quantity <= remaining) {
                    filled = plus(filled, lot.quantity)
                    remaining = minus(remaining, lot.quantity)
                } else {
                    val leftoverQty = minus(lot.quantity, remaining)
                    val leftoverCost = times(lot.cost, div(leftoverQty, lot.quantity))
                    queue.addFirst(SourceLot(lot.source, leftoverQty, leftoverCost))
                    filled = plus(filled, remaining)
                    remaining = ZERO
                }
            }
            return filled
        }

        private fun investedBySource(): Map<BrokerSource, BigDecimal> {
            val values = HashMap<BrokerSource, BigDecimal>()
            localBy.forEach { (key, value) ->
                values[key.first] = plus(values[key.first] ?: ZERO, value)
            }
            lots.forEach { (assetId, queue) ->
                val unit = unitByAsset[assetId]
                queue.forEach { lot ->
                    val value = unit?.let { times(lot.quantity, it) } ?: lot.cost
                    values[lot.source] = plus(values[lot.source] ?: ZERO, value)
                }
            }
            return values
        }
    }

    private data class SourceLot(val source: BrokerSource, val quantity: BigDecimal, val cost: BigDecimal)
}
