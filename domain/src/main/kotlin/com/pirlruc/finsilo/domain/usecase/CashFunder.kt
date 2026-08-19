package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal

/**
 * Same-day cash deposit that closes a buy's cash gap.
 *
 * Used by CSV import and by manual ledger saves so a purchase can be booked
 * without a separate deposit step. The deposit still exists as a ledger row
 * (O3 cash guard holds after the pair is applied).
 */
class CashFunder(private val ledger: PositionLedger = PositionLedger()) {
    fun depositFor(snapshot: PortfolioSnapshot, request: LedgerEntryRequest, asset: Asset): LedgerEntryRequest? {
        if (request.type != TransactionType.BUY) return null
        val rate = executionRate(snapshot, request, asset) ?: return null
        val unitEur = toEur(request.unitPriceNative, asset.baseCurrency, rate)
        val cost = plus(times(request.quantity, unitEur), request.feesEur)
        val assets = snapshot.assets.associateBy { it.id } + (asset.id to asset)
        val cash = ledger.cashEur(snapshot.transactions, assets)
        val gap = cost.subtract(cash)
        if (gap.signum() <= 0) return null
        return LedgerEntryRequest(
            type = TransactionType.DEPOSIT_CASH,
            date = request.date,
            quantity = gap,
            unitPriceNative = BigDecimal.ONE,
            feesEur = BigDecimal.ZERO,
        )
    }

    private fun executionRate(snapshot: PortfolioSnapshot, request: LedgerEntryRequest, asset: Asset): BigDecimal? {
        if (asset.baseCurrency == Currency.EUR) return BigDecimal.ONE
        return request.eurPerUsd ?: ledger.eurPerUsdOn(request.date, snapshot.fxRates)
    }
}
