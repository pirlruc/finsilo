package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.CONTEXT
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import java.math.BigDecimal
import java.time.LocalDate

data class LotPosition(
    val quantity: BigDecimal,
    val remainingCostEur: BigDecimal,
) {
    val averageCostEur: BigDecimal
        get() = if (quantity.signum() == 0) ZERO else div(remainingCostEur, quantity)
}

/**
 * Reconstructs holdings, cash, and locally valued instruments from the transaction ledger.
 *
 * Average cost is a moving average: buys increase quantity and remaining cost; sells reduce
 * remaining cost proportionally and leave the average unchanged.
 */
class PositionLedger {

    fun transactionsOnOrBefore(transactions: List<Transaction>, date: LocalDate): List<Transaction> =
        transactions.filter { !it.date.isAfter(date) }.sortedWith(compareBy({ it.date }, { it.id }))

    fun position(transactions: List<Transaction>): LotPosition {
        var qty = ZERO
        var cost = ZERO
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.BUY -> {
                    qty = plus(qty, tx.quantity)
                    cost = plus(cost, plus(tx.notionalEur, tx.feesEur))
                }
                TransactionType.SELL -> {
                    if (qty.signum() <= 0) continue
                    val sellQty = MoneyMath.min(tx.quantity, qty)
                    val avg = div(cost, qty)
                    cost = minus(cost, times(avg, sellQty))
                    qty = minus(qty, sellQty)
                    if (qty.signum() == 0) cost = ZERO
                }
                TransactionType.DEPOSIT_CASH,
                TransactionType.DIVIDEND,
                TransactionType.INTEREST,
                -> Unit
            }
        }
        return LotPosition(quantity = qty, remainingCostEur = cost)
    }

    /**
     * Deposits and CTs have no market price. RFC: value = total deposits + total interest
     * (withdrawals via SELL reduce the balance).
     */
    fun locallyValuedEur(transactions: List<Transaction>): BigDecimal {
        var value = ZERO
        for (tx in transactions) {
            value = when (tx.type) {
                TransactionType.BUY -> plus(value, minus(tx.notionalEur, tx.feesEur))
                TransactionType.INTEREST -> plus(value, tx.notionalEur)
                TransactionType.SELL -> minus(value, tx.notionalEur)
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
                TransactionType.BUY -> minus(cash, plus(tx.notionalEur, tx.feesEur))
                TransactionType.SELL -> plus(cash, minus(tx.notionalEur, tx.feesEur))
                TransactionType.DIVIDEND, TransactionType.INTEREST ->
                    if (locallyValued) cash else plus(cash, tx.notionalEur)
            }
        }
        return cash
    }

    fun usdPerEurOn(date: LocalDate, rates: List<CurrencyRate>): BigDecimal {
        val rate = rates.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }
        return rate?.usdPerEur ?: BigDecimal.ONE
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
