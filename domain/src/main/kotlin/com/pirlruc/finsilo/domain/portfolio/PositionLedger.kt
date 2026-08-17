package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.min
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import java.math.BigDecimal
import java.time.LocalDate
import java.util.ArrayDeque

/** Open FIFO lot (quantity still held and remaining EUR cost). */
data class FifoLot(val quantity: BigDecimal, val remainingCostEur: BigDecimal)

/** Aggregated open lots for one instrument. */
data class LotPosition(val quantity: BigDecimal, val remainingCostEur: BigDecimal, val lots: List<FifoLot> = emptyList()) {
    val averageCostEur: BigDecimal
        get() = if (quantity.signum() == 0) ZERO else div(remainingCostEur, quantity)
}

/** Reconstructs holdings, cash, and locally valued instruments from the transaction ledger. */
class PositionLedger {
    fun ordered(transactions: List<Transaction>): List<Transaction> =
        transactions.sortedWith(compareBy({ it.date }, { it.type.ledgerRank }, { it.id }))

    fun transactionsOnOrBefore(transactions: List<Transaction>, date: LocalDate): List<Transaction> =
        ordered(transactions.filter { !it.date.isAfter(date) })

    /** Ledger rows that replay strictly before [candidate] (date, type rank, then id). */
    fun preceding(transactions: List<Transaction>, candidate: Transaction): List<Transaction> =
        ordered(transactions.filter { comesBefore(it, candidate) })

    private fun comesBefore(left: Transaction, right: Transaction): Boolean {
        if (left.date != right.date) return left.date.isBefore(right.date)
        val rank = left.type.ledgerRank.compareTo(right.type.ledgerRank)
        if (rank != 0) return rank < 0
        return left.id < right.id
    }

    fun position(transactions: List<Transaction>): LotPosition {
        val lots = ArrayDeque<FifoLot>()
        for (tx in ordered(transactions)) {
            when (tx.type) {
                TransactionType.BUY -> lots.addLast(FifoLot(tx.quantity, plus(tx.notionalEur, tx.feesEur)))
                TransactionType.SELL -> {
                    consumeFifo(lots, tx.quantity)
                    Unit
                }
                TransactionType.DEPOSIT_CASH,
                TransactionType.WITHDRAWAL,
                TransactionType.DIVIDEND,
                TransactionType.INTEREST,
                -> Unit
            }
        }
        val qty = lots.fold(ZERO) { acc, lot -> plus(acc, lot.quantity) }
        val cost = lots.fold(ZERO) { acc, lot -> plus(acc, lot.remainingCostEur) }
        return LotPosition(quantity = qty, remainingCostEur = cost, lots = lots.toList())
    }

    /** @return quantity actually filled from open lots (may be less than [sellQty]). */
    private fun consumeFifo(lots: ArrayDeque<FifoLot>, sellQty: BigDecimal): BigDecimal {
        var remaining = sellQty
        var filled = ZERO
        while (remaining.signum() > 0 && lots.isNotEmpty()) {
            val lot = lots.removeFirst()
            val cmp = lot.quantity.compareTo(remaining)
            if (cmp <= 0) {
                filled = plus(filled, lot.quantity)
                remaining = minus(remaining, lot.quantity)
            } else {
                val leftoverQty = minus(lot.quantity, remaining)
                val leftoverCost = times(lot.remainingCostEur, div(leftoverQty, lot.quantity))
                lots.addFirst(FifoLot(leftoverQty, leftoverCost))
                filled = plus(filled, remaining)
                remaining = ZERO
            }
        }
        return filled
    }

    /**
     * Deposits and CTs have no market price. Value = total deposits + total interest
     * (redemptions via SELL reduce the balance). Portfolio-level WITHDRAWAL
     * only affects uninvested cash.
     */
    fun locallyValuedEur(transactions: List<Transaction>): BigDecimal {
        var value = ZERO
        for (tx in ordered(transactions)) {
            value =
                when (tx.type) {
                    TransactionType.BUY -> plus(value, plus(tx.notionalEur, tx.feesEur))
                    TransactionType.INTEREST, TransactionType.DIVIDEND -> plus(value, tx.notionalEur)
                    TransactionType.SELL -> minus(value, tx.notionalEur)
                    TransactionType.DEPOSIT_CASH, TransactionType.WITHDRAWAL -> value
                }
        }
        return value
    }

    /**
     * Uninvested EUR cash. Interest and dividends on locally valued instruments stay inside
     * those instruments; on marketable assets they are paid out to cash.
     */
    fun cashEur(transactions: List<Transaction>, assetsById: Map<String, Asset>): BigDecimal {
        var cash = ZERO
        val lotsByAsset = HashMap<String, ArrayDeque<FifoLot>>()
        for (tx in ordered(transactions)) {
            cash = applyCash(tx, cash, assetsById[tx.assetId]?.locallyValued == true, lotsByAsset)
        }
        return cash
    }

    private fun applyCash(
        tx: Transaction,
        cash: BigDecimal,
        locallyValued: Boolean,
        lotsByAsset: HashMap<String, ArrayDeque<FifoLot>>,
    ): BigDecimal = when (tx.type) {
        TransactionType.DEPOSIT_CASH -> plus(cash, tx.notionalEur)
        TransactionType.WITHDRAWAL -> minus(cash, min(tx.notionalEur, cash))
        TransactionType.BUY -> {
            lotsByAsset
                .getOrPut(tx.assetId) { ArrayDeque() }
                .addLast(FifoLot(tx.quantity, plus(tx.notionalEur, tx.feesEur)))
            val cost = plus(tx.notionalEur, tx.feesEur)
            if (cost <= cash) minus(cash, cost) else ZERO
        }
        TransactionType.SELL -> {
            val filled = consumeFifo(lotsByAsset.getOrPut(tx.assetId) { ArrayDeque() }, tx.quantity)
            plus(cash, minus(times(tx.unitPriceEur, filled), tx.feesEur))
        }
        TransactionType.DIVIDEND, TransactionType.INTEREST ->
            if (locallyValued) cash else plus(cash, tx.notionalEur)
    }

    fun buyCostEur(tx: Transaction): BigDecimal = plus(tx.notionalEur, tx.feesEur)

    fun eurPerUsdOn(date: LocalDate, rates: List<CurrencyRate>): BigDecimal? {
        if (rates.isEmpty()) return null
        rates
            .filter { !it.date.isAfter(date) }
            .maxByOrNull { it.date }
            ?.eurPerUsd
            ?.let { return it }
        // A single latest quote must still value earlier NAV points; do not silently use 1.0.
        return rates.minBy { it.date }.eurPerUsd
    }

    fun marketOnOrBefore(assetId: String, date: LocalDate, byAsset: Map<String, List<DailyMarketData>>): DailyMarketData? {
        val rows = byAsset[assetId] ?: return null
        return rows.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }
    }

    fun nativePrice(
        assetId: String,
        date: LocalDate,
        byAsset: Map<String, List<DailyMarketData>>,
        assetTransactions: List<Transaction>,
    ): BigDecimal? {
        marketOnOrBefore(assetId, date, byAsset)?.closingPriceNative?.let { return it }
        return assetTransactions
            .filter {
                !it.date.isAfter(date) &&
                    (it.type == TransactionType.BUY || it.type == TransactionType.SELL)
            }.maxByOrNull { it.date }
            ?.unitPriceNative
    }

    fun indexMarket(marketData: List<DailyMarketData>): Map<String, List<DailyMarketData>> =
        marketData.groupBy { it.assetId }.mapValues { (_, rows) -> rows.sortedBy { it.date } }
}
