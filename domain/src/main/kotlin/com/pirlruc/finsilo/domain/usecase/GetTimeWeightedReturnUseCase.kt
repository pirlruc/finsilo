package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.TwrReport
import com.pirlruc.finsilo.domain.model.TwrSplit
import com.pirlruc.finsilo.domain.model.TwrSubPeriod
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.HUNDRED
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Time-weighted return. Sub-periods open only on buys funded with external cash
 * (cost exceeds uninvested cash) and on withdrawals. Internal buys, sells,
 * deposits that only fund cash, dividends, and interest do not split.
 * [TwrSubPeriod.split] is the event that opened that sub-period.
 */
class GetTimeWeightedReturnUseCase(
    private val valuator: PortfolioValuator = PortfolioValuator(),
    private val ledger: PositionLedger = PositionLedger(),
) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): TwrReport {
        val ordered = snapshot.transactions.sortedWith(compareBy({ it.date }, { it.id }))
        if (ordered.isEmpty()) {
            return TwrReport(asOf = asOf, twrPercent = ZERO, subPeriods = emptyList())
        }
        val walk = TwrWalk(snapshot, asOf)
        for (tx in ordered) {
            if (tx.date.isAfter(asOf)) break
            walk.apply(tx)
        }
        return walk.finish()
    }

    private inner class TwrWalk(private val snapshot: PortfolioSnapshot, private val asOf: LocalDate) {
        private val assetsById = snapshot.assets.associateBy { it.id }
        private val seen = ArrayList<Transaction>()
        private val periods = ArrayList<TwrSubPeriod>()
        private var cash = ZERO
        private var periodStart: LocalDate? = null
        private var startNav: BigDecimal? = null
        private var openedBy: TwrSplit? = null

        fun apply(tx: Transaction) {
            val split = splitReason(tx, cash)
            closeOpenPeriod(tx.date, split)
            seen += tx
            cash = ledger.cashEur(seen, assetsById)
            if (split != null || startNav == null) {
                startNav = navWith(seen, tx.date)
                periodStart = tx.date
                openedBy = split
            }
        }

        fun finish(): TwrReport {
            val open = openPeriod()
            if (open != null) {
                periods +=
                    period(
                        open.first,
                        asOf,
                        open.second,
                        navWith(seen.filter { !it.date.isAfter(asOf) }, asOf),
                    )
            }
            val product =
                periods.fold(BigDecimal.ONE) { acc, period ->
                    times(acc, plus(BigDecimal.ONE, div(period.returnPercent, HUNDRED)))
                }
            return TwrReport(
                asOf = asOf,
                twrPercent = times(minus(product, BigDecimal.ONE), HUNDRED),
                subPeriods = periods,
            )
        }

        private fun closeOpenPeriod(on: LocalDate, split: TwrSplit?) {
            if (split == null) return
            val (from, start) = openPeriod() ?: return
            periods += period(from, on, start, navWith(seen, on))
        }

        private fun openPeriod(): Pair<LocalDate, BigDecimal>? {
            val start = startNav ?: return null
            val from = periodStart ?: return null
            return if (start.signum() == 0) null else from to start
        }

        private fun period(from: LocalDate, to: LocalDate, start: BigDecimal, end: BigDecimal): TwrSubPeriod {
            val ret = minus(div(end, start), BigDecimal.ONE)
            return TwrSubPeriod(
                from = from,
                to = to,
                returnPercent = times(ret, HUNDRED),
                split = openedBy,
            )
        }

        private fun navWith(txs: List<Transaction>, date: LocalDate): BigDecimal =
            valuator.totalNavEur(snapshot.copy(transactions = txs), date)
    }

    private fun splitReason(tx: Transaction, cashBefore: BigDecimal): TwrSplit? = when (tx.type) {
        TransactionType.WITHDRAWAL -> TwrSplit.WITHDRAWAL
        TransactionType.BUY -> {
            val cost = ledger.buyCostEur(tx)
            if (cost > cashBefore) TwrSplit.EXTERNAL_BUY else null
        }
        else -> null
    }
}
