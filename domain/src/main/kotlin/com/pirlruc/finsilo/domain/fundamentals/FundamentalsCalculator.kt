package com.pirlruc.finsilo.domain.fundamentals

import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import java.math.BigDecimal

/**
 * Equity ratios. A metric is null when earnings are negative, growth is missing,
 * or capital is not positive. Banks are not a special case.
 */
object FundamentalsCalculator {
    fun metrics(input: FundamentalsInputs): ValuationMetrics {
        val pe = priceToEarnings(input.price, input.ttmEps)
        return ValuationMetrics(
            pe = pe,
            peg = peg(pe, input.epsGrowthPercent),
            fcfYield = fcfYield(input.operatingCashFlowTtm, input.capexTtm, input.marketCap),
            roe = overPositiveCapital(input.ttmNetIncome, input.averageEquity),
            roic = roic(input),
            debtToEquity = overPositiveCapital(input.totalDebt, input.equity),
            evToEbit = evToEbit(input),
            interestCoverage = interestCoverage(input.ebitTtm, input.interestExpense),
        )
    }

    fun priceToEarnings(price: BigDecimal?, eps: BigDecimal?): BigDecimal? {
        if (price == null || eps == null || price.signum() < 0 || eps.signum() <= 0) return null
        return MoneyMath.div(price, eps)
    }

    fun peg(pe: BigDecimal?, growthPercent: BigDecimal?): BigDecimal? {
        if (pe == null || growthPercent == null || growthPercent.signum() <= 0) return null
        return MoneyMath.div(pe, growthPercent)
    }

    fun fcfYield(operatingCashFlow: BigDecimal?, capex: BigDecimal?, marketCap: BigDecimal?): BigDecimal? {
        if (operatingCashFlow == null || capex == null || marketCap == null || marketCap.signum() <= 0) return null
        return MoneyMath.div(MoneyMath.minus(operatingCashFlow, capex), marketCap)
    }

    fun overPositiveCapital(numerator: BigDecimal?, capital: BigDecimal?): BigDecimal? {
        if (numerator == null || capital == null || capital.signum() <= 0) return null
        return MoneyMath.div(numerator, capital)
    }

    fun roic(input: FundamentalsInputs): BigDecimal? {
        val ebit = input.ebitTtm
        val tax = input.taxRate
        val debt = input.totalDebt
        val equity = input.equity
        val cash = input.cash
        if (ebit == null || tax == null || debt == null || equity == null || cash == null) return null
        if (ebit.signum() <= 0 || tax.signum() < 0 || tax > BigDecimal.ONE) return null
        val invested = debt.add(equity).subtract(cash)
        if (invested.signum() <= 0) return null
        return MoneyMath.div(ebit.multiply(BigDecimal.ONE.subtract(tax)), invested)
    }

    fun evToEbit(input: FundamentalsInputs): BigDecimal? {
        val cap = input.marketCap
        val debt = input.totalDebt
        val cash = input.cash
        val ebit = input.ebitTtm
        if (cap == null || debt == null || cash == null || ebit == null || ebit.signum() <= 0) return null
        return MoneyMath.div(cap.add(debt).subtract(cash), ebit)
    }

    fun interestCoverage(ebit: BigDecimal?, interest: BigDecimal?): BigDecimal? {
        if (ebit == null || interest == null || ebit.signum() <= 0 || interest.signum() <= 0) return null
        return MoneyMath.div(ebit, interest)
    }
}
