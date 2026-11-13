package com.pirlruc.finsilo.domain.performance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.ln

/** FIRE number and years, plus the note that this is not advice. */
data class FirePlan(val fireNumberEur: BigDecimal, val years: BigDecimal?, val note: String)

/** Forward dividend cash that a DRIP plan would reinvest. */
data class DripPlan(val forwardIncomeEur: BigDecimal, val note: String)

/**
 * FIRE number from annual expenses and a safe withdrawal rate, and years to reach it.
 * Not financial advice.
 */
object FirePlanner {
    const val NOTE: String = "Not financial advice."

    /** Portfolio size that supports [annualExpensesEur] at [withdrawalRate]. Null when inputs are not positive. */
    fun number(annualExpensesEur: BigDecimal, withdrawalRate: BigDecimal): BigDecimal? {
        if (annualExpensesEur.signum() <= 0 || withdrawalRate.signum() <= 0) return null
        if (withdrawalRate > BigDecimal.ONE) return null
        return annualExpensesEur.divide(withdrawalRate, 2, RoundingMode.HALF_UP)
    }

    /**
     * Years until [portfolioEur] reaches [fireNumberEur] with [annualSavingsEur] and [annualReturn].
     * A zero return uses linear savings. Already being there is zero years.
     */
    fun years(portfolioEur: BigDecimal, fireNumberEur: BigDecimal, annualSavingsEur: BigDecimal, annualReturn: BigDecimal): BigDecimal? {
        if (fireNumberEur.signum() <= 0 || annualSavingsEur.signum() < 0) return null
        if (portfolioEur >= fireNumberEur) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        if (annualReturn.signum() == 0) return linear(portfolioEur, fireNumberEur, annualSavingsEur)
        return compounded(portfolioEur, fireNumberEur, annualSavingsEur, annualReturn)
    }

    /** Number, years, and the advice note. */
    fun plan(
        portfolioEur: BigDecimal,
        annualExpensesEur: BigDecimal,
        withdrawalRate: BigDecimal,
        annualSavingsEur: BigDecimal,
        annualReturn: BigDecimal,
    ): FirePlan? {
        val target = number(annualExpensesEur, withdrawalRate) ?: return null
        return FirePlan(target, years(portfolioEur, target, annualSavingsEur, annualReturn), NOTE)
    }

    private fun linear(portfolioEur: BigDecimal, fireNumberEur: BigDecimal, annualSavingsEur: BigDecimal): BigDecimal? {
        if (annualSavingsEur.signum() == 0) return null
        return fireNumberEur.subtract(portfolioEur).divide(annualSavingsEur, 2, RoundingMode.HALF_UP)
    }

    private fun compounded(
        portfolioEur: BigDecimal,
        fireNumberEur: BigDecimal,
        annualSavingsEur: BigDecimal,
        annualReturn: BigDecimal,
    ): BigDecimal? {
        val rate = annualReturn.toDouble()
        val top = fireNumberEur.toDouble() * rate + annualSavingsEur.toDouble()
        val bottom = portfolioEur.toDouble() * rate + annualSavingsEur.toDouble()
        if (top <= 0.0 || bottom <= 0.0) return null
        val span = ln(top / bottom) / ln(1.0 + rate)
        if (span.isNaN() || span.isInfinite() || span < 0.0) return null
        return BigDecimal.valueOf(span).setScale(2, RoundingMode.HALF_UP)
    }
}

/** DRIP forward income taken from [DividendCalendar]. Not financial advice. */
object DividendReinvestment {
    const val NOTE: String = "Not financial advice."

    /** Forward 12-month income that would be reinvested. Null when the calendar will not project. */
    fun project(dates: List<LocalDate>, lastAmountEur: BigDecimal, from: LocalDate): DripPlan? {
        val calendar = DividendCalendar.project(dates, lastAmountEur, from) ?: return null
        return DripPlan(calendar.forwardIncomeEur, NOTE)
    }
}
