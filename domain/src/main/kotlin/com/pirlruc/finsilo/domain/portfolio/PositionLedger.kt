package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import java.math.BigDecimal
import java.time.LocalDate
import java.util.ArrayDeque

data class FifoLot(
    val quantity: BigDecimal,
    val remainingCostEur: BigDecimal,
)

data class LotPosition(
    val quantity: BigDecimal,
    val remainingCostEur: BigDecimal,
    val lots: List<FifoLot> = emptyList(),
) {
    val averageCostEur: BigDecimal
        get() = if (quantity.signum() == 0) ZERO else div(remainingCostEur, quantity)
}

/**
 * Reconstructs holdings, cash, and locally valued instruments from the transaction ledger.
 *
 * Open lots use FIFO (oldest buy consumed first), which matches Portuguese capital-gains
 * reporting more closely than a moving average.
 */
class PositionLedger {

    fun transactionsOnOrBefore(transactions: List<Transaction>, date: LocalDate): List<Transaction> =
        transactions.filter { !it.date.isAfter(date) }.sortedWith(compareBy({ it.date }, { it.id }))

    fun position(transactions: List<Transaction>): LotPosition {
        val lots = ArrayDeque<FifoLot>()
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.BUY -> lots.addLast(FifoLot(tx.quantity, plus(tx.notionalEur, tx.feesEur)))
                TransactionType.SELL -> consumeFifo(lots, tx.quantity)
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

    private fun consumeFifo(lots: ArrayDeque<FifoLot>, sellQty: BigDecimal) {
        var remaining = sellQty
        while (remaining.signum() > 0 && lots.isNotEmpty()) {
            val lot = lots.removeFirst()
            val cmp = lot.quantity.compareTo(remaining)
            if (cmp <= 0) {
                remaining = minus(remaining, lot.quantity)
            } else {
                val leftoverQty = minus(lot.quantity, remaining)
                val leftoverCost = times(lot.remainingCostEur, div(leftoverQty, lot.quantity))
                lots.addFirst(FifoLot(leftoverQty, leftoverCost))
                remaining = ZERO
            }
        }
    }

    /**
     * Deposits and CTs have no market price. Value = total deposits + total interest
     * (withdrawals via SELL reduce the balance).
     */
    fun locallyValuedEur(transactions: List<Transaction>): BigDecimal {
        var value = ZERO
        for (tx in transactions) {
            value = when (tx.type) {
                TransactionType.BUY -> plus(value, minus(tx.notionalEur, tx.feesEur))
                TransactionType.INTEREST -> plus(value, tx.notionalEur)
                TransactionType.SELL, TransactionType.WITHDRAWAL -> minus(value, tx.notionalEur)
                TransactionType.DEPOSIT_CASH, TransactionType.DIVIDEND -> value
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
        for (tx in transactions) {
            val asset = assetsById[tx.assetId]
            val locallyValued = asset?.assetType?.isLocallyValued == true
            cash = when (tx.type) {
                TransactionType.DEPOSIT_CASH -> plus(cash, tx.notionalEur)
                TransactionType.WITHDRAWAL -> minus(cash, tx.notionalEur)
                TransactionType.BUY -> {
                    val cost = plus(tx.notionalEur, tx.feesEur)
                    if (cost <= cash) minus(cash, cost) else ZERO
                }
                TransactionType.SELL -> plus(cash, minus(tx.notionalEur, tx.feesEur))
                TransactionType.DIVIDEND, TransactionType.INTEREST ->
                    if (locallyValued) cash else plus(cash, tx.notionalEur)
            }
        }
        return cash
    }

    fun buyCostEur(tx: Transaction): BigDecimal = plus(tx.notionalEur, tx.feesEur)

    fun eurPerUsdOn(date: LocalDate, rates: List<CurrencyRate>): BigDecimal {
        val rate = rates.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }
        return rate?.eurPerUsd ?: BigDecimal.ONE
    }

    fun marketOnOrBefore(
        assetId: String,
        date: LocalDate,
        byAsset: Map<String, List<DailyMarketData>>,
    ): DailyMarketData? {
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
            }
            .maxByOrNull { it.date }
            ?.unitPriceNative
    }

    fun indexMarket(marketData: List<DailyMarketData>): Map<String, List<DailyMarketData>> =
        marketData.groupBy { it.assetId }.mapValues { (_, rows) -> rows.sortedBy { it.date } }
}
