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

        val assetsById = snapshot.assets.associateBy { it.id }
        val seen = ArrayList<Transaction>()
        var cash = ZERO
        var periodStart: LocalDate? = null
        var startNav: BigDecimal? = null
        var openedBy: TwrSplit? = null
        val periods = ArrayList<TwrSubPeriod>()

        fun navWith(txs: List<Transaction>, date: LocalDate): BigDecimal =
            valuator.totalNavEur(snapshot.copy(transactions = txs), date)

        for (tx in ordered) {
            if (tx.date.isAfter(asOf)) break
            val split = splitReason(tx, cash)
            if (split != null && startNav != null && periodStart != null && startNav.signum() != 0) {
                val navBefore = navWith(seen, tx.date)
                val ret = minus(div(navBefore, startNav), BigDecimal.ONE)
                periods += TwrSubPeriod(
                    from = periodStart,
                    to = tx.date,
                    returnPercent = times(ret, HUNDRED),
                    split = openedBy,
                )
            }
            seen += tx
            cash = ledger.cashEur(seen, assetsById)
            if (split != null || startNav == null) {
                startNav = navWith(seen, tx.date)
                periodStart = tx.date
                openedBy = split
            }
        }

        if (startNav != null && periodStart != null && startNav.signum() != 0) {
            val endNav = navWith(seen.filter { !it.date.isAfter(asOf) }, asOf)
            val ret = minus(div(endNav, startNav), BigDecimal.ONE)
            periods += TwrSubPeriod(
                from = periodStart,
                to = asOf,
                returnPercent = times(ret, HUNDRED),
                split = openedBy,
            )
        }

        val product = periods.fold(BigDecimal.ONE) { acc, period ->
            times(acc, plus(BigDecimal.ONE, div(period.returnPercent, HUNDRED)))
        }
        val twrPercent = times(minus(product, BigDecimal.ONE), HUNDRED)
        return TwrReport(asOf = asOf, twrPercent = twrPercent, subPeriods = periods)
    }

    private fun splitReason(tx: Transaction, cashBefore: BigDecimal): TwrSplit? =
        when (tx.type) {
            TransactionType.WITHDRAWAL -> TwrSplit.WITHDRAWAL
            TransactionType.BUY -> {
                val cost = ledger.buyCostEur(tx)
                if (cost > cashBefore) TwrSplit.EXTERNAL_BUY else null
            }
            else -> null
        }
}
