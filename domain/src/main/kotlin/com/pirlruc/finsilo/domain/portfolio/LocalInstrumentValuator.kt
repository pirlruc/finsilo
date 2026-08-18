package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import java.math.BigDecimal
import java.time.LocalDate

internal object LocalInstrumentValuator {
    fun holding(
        ledger: PositionLedger,
        asset: Asset,
        txs: List<Transaction>,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        eurPerUsd: BigDecimal?,
    ): HoldingValuation? {
        val market = ledger.marketOnOrBefore(asset.id, asOf, marketByAsset)
        if (market == null) return cashFlowHolding(ledger, asset, txs)
        val lot = ledger.position(txs)
        if (lot.quantity.signum() == 0) return null
        val currency = asset.baseCurrency
        val rate = if (currency == Currency.EUR) BigDecimal.ONE else eurPerUsd
        val priceEur = rate?.let { toEur(market.closingPriceNative, currency, it) } ?: return null
        val value = times(lot.quantity, priceEur)
        return HoldingValuation(
            asset = asset,
            quantity = lot.quantity,
            priceEur = priceEur,
            valueEur = value,
            costEur = lot.remainingCostEur,
            unrealizedPnlEur = minus(value, lot.remainingCostEur),
            priceNative = market.closingPriceNative,
            quoteCurrency = currency,
        )
    }

    fun cashFlowHolding(ledger: PositionLedger, asset: Asset, txs: List<Transaction>): HoldingValuation? {
        val value = ledger.locallyValuedEur(txs)
        if (value.signum() == 0) return null
        val cost = remainingPrincipalCost(txs)
        return HoldingValuation(
            asset = asset,
            quantity = BigDecimal.ONE,
            priceEur = value,
            valueEur = value,
            costEur = cost,
            unrealizedPnlEur = minus(value, cost),
            priceNative = value,
            quoteCurrency = Currency.EUR,
        )
    }

    fun remainingPrincipalCost(transactions: List<Transaction>): BigDecimal {
        var cost = ZERO
        for (tx in transactions) {
            cost =
                when (tx.type) {
                    TransactionType.BUY -> plus(cost, plus(tx.notionalEur, tx.feesEur))
                    TransactionType.SELL -> {
                        val reduced = minus(cost, tx.notionalEur)
                        if (reduced.signum() < 0) ZERO else reduced
                    }
                    else -> cost
                }
        }
        return cost
    }
}
