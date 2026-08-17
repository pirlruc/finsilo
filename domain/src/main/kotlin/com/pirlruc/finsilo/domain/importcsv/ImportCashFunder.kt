package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import java.math.BigDecimal

/** Inserts a same-day cash deposit when a CSV buy would otherwise fail the cash guard. */
internal class ImportCashFunder(private val ledger: PositionLedger = PositionLedger()) {
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
