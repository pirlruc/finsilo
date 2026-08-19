package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.RealizedGainsReport
import com.pirlruc.finsilo.domain.model.RealizedKind
import com.pirlruc.finsilo.domain.model.RealizedLotLine
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.FifoLot
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.util.ArrayDeque

/**
 * Calendar-year FIFO realized gains from stored EUR prices.
 * Locally valued redemptions are labeled separately from marketable disposals.
 * Does not change [PositionLedger] quantity math.
 */
class GetRealizedGainsUseCase(private val ledger: PositionLedger = PositionLedger()) {
    operator fun invoke(snapshot: PortfolioSnapshot, year: Int): RealizedGainsReport {
        val assets = snapshot.assets.associateBy { it.id }
        val lots = HashMap<String, ArrayDeque<FifoLot>>()
        val lines = ArrayList<RealizedLotLine>()
        for (tx in ledger.ordered(snapshot.transactions)) {
            when (tx.type) {
                TransactionType.BUY ->
                    lots.getOrPut(tx.assetId) { ArrayDeque() }
                        .addLast(FifoLot(tx.quantity, plus(tx.notionalEur, tx.feesEur), tx.date))
                TransactionType.SELL -> lines += realize(tx, assets[tx.assetId], lots, year)
                else -> Unit
            }
        }
        val total = lines.fold(ZERO) { acc, line -> plus(acc, line.gainEur) }
        return RealizedGainsReport(year = year, lines = lines, totalGainEur = total)
    }

    private fun realize(tx: Transaction, asset: Asset?, lots: HashMap<String, ArrayDeque<FifoLot>>, year: Int): List<RealizedLotLine> {
        if (asset == null) {
            consume(lots.getOrPut(tx.assetId) { ArrayDeque() }, tx.quantity)
            return emptyList()
        }
        if (tx.date.year != year) {
            consume(lots.getOrPut(tx.assetId) { ArrayDeque() }, tx.quantity)
            return emptyList()
        }
        val fills = consume(lots.getOrPut(tx.assetId) { ArrayDeque() }, tx.quantity)
        val filled = fills.fold(ZERO) { acc, fill -> plus(acc, fill.quantity) }
        if (filled.signum() == 0) return emptyList()
        val kind = if (asset.locallyValued) RealizedKind.REDEMPTION else RealizedKind.DISPOSAL
        val netProceeds = minus(times(tx.unitPriceEur, filled), tx.feesEur)
        return fills.map { fill ->
            val share = div(fill.quantity, filled)
            val proceeds = times(netProceeds, share)
            RealizedLotLine(
                asset = asset,
                sellDate = tx.date,
                acquiredDate = fill.acquiredDate,
                quantity = fill.quantity,
                costEur = fill.remainingCostEur,
                proceedsEur = proceeds,
                gainEur = minus(proceeds, fill.remainingCostEur),
                kind = kind,
            )
        }
    }

    private fun consume(lots: ArrayDeque<FifoLot>, sellQty: BigDecimal): List<FifoLot> {
        var remaining = sellQty
        val filled = ArrayList<FifoLot>()
        while (remaining.signum() > 0 && lots.isNotEmpty()) {
            val lot = lots.removeFirst()
            if (lot.quantity.compareTo(remaining) <= 0) {
                filled += lot
                remaining = minus(remaining, lot.quantity)
            } else {
                val cost = times(lot.remainingCostEur, div(remaining, lot.quantity))
                val leftoverQty = minus(lot.quantity, remaining)
                val leftoverCost = minus(lot.remainingCostEur, cost)
                lots.addFirst(FifoLot(leftoverQty, leftoverCost, lot.acquiredDate))
                filled += FifoLot(remaining, cost, lot.acquiredDate)
                remaining = ZERO
            }
        }
        return filled
    }
}
