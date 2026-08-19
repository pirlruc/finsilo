package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import java.math.BigDecimal
import java.time.LocalDate

internal object MarketInstrumentValuator {
    fun holding(
        ledger: PositionLedger,
        asset: Asset,
        txs: List<Transaction>,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        eurPerUsd: BigDecimal?,
    ): HoldingValuation? {
        val lot = ledger.position(txs)
        if (lot.quantity.signum() == 0) return null
        val priceEur = pricedInEur(ledger, asset, asOf, marketByAsset, txs, eurPerUsd) ?: return null
        val value = times(lot.quantity, priceEur)
        val market = ledger.marketOnOrBefore(asset.id, asOf, marketByAsset)
        val native = ledger.nativePrice(asset.id, asOf, marketByAsset, txs)
        val quote = if (market != null) QuoteCurrency.of(asset) else asset.baseCurrency
        return HoldingValuation(
            asset = asset,
            quantity = lot.quantity,
            priceEur = priceEur,
            valueEur = value,
            costEur = lot.remainingCostEur,
            unrealizedPnlEur = minus(value, lot.remainingCostEur),
            priceNative = native,
            quoteCurrency = quote,
        )
    }

    /**
     * Live USD feeds convert with EUR-per-USD even when booked in EUR.
     * Last-trade fallback stays in the booking currency.
     */
    fun pricedInEur(
        ledger: PositionLedger,
        asset: Asset,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        txs: List<Transaction>,
        eurPerUsd: BigDecimal?,
    ): BigDecimal? {
        val market = ledger.marketOnOrBefore(asset.id, asOf, marketByAsset)
        val native = ledger.nativePrice(asset.id, asOf, marketByAsset, txs) ?: return null
        val currency = if (market != null) QuoteCurrency.of(asset) else asset.baseCurrency
        val rate = if (currency == Currency.EUR) BigDecimal.ONE else eurPerUsd
        return rate?.let { toEur(native, currency, it) }
    }
}
