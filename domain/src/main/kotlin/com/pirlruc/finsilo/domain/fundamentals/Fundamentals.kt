package com.pirlruc.finsilo.domain.fundamentals

import java.math.BigDecimal
import java.time.LocalDate

/** Statement inputs. Missing or unusable figures stay null so a ratio is not invented. */
data class FundamentalsInputs(
    val price: BigDecimal? = null,
    val ttmEps: BigDecimal? = null,
    val epsGrowthPercent: BigDecimal? = null,
    val operatingCashFlowTtm: BigDecimal? = null,
    val capexTtm: BigDecimal? = null,
    val marketCap: BigDecimal? = null,
    val ttmNetIncome: BigDecimal? = null,
    val averageEquity: BigDecimal? = null,
    val ebitTtm: BigDecimal? = null,
    val taxRate: BigDecimal? = null,
    val totalDebt: BigDecimal? = null,
    val equity: BigDecimal? = null,
    val cash: BigDecimal? = null,
    val interestExpense: BigDecimal? = null,
)

/** Ratios. Null means the figure is undefined for these inputs. */
data class ValuationMetrics(
    val pe: BigDecimal? = null,
    val peg: BigDecimal? = null,
    val fcfYield: BigDecimal? = null,
    val roe: BigDecimal? = null,
    val roic: BigDecimal? = null,
    val debtToEquity: BigDecimal? = null,
    val evToEbit: BigDecimal? = null,
    val interestCoverage: BigDecimal? = null,
)

/** Why a card is showing numbers, a gap, or nothing for this instrument. */
enum class ValuationNote {
    RATIOS,
    NOT_APPLICABLE,
    MISSING,
    BUDGET,
}

data class FundamentalView(
    val note: ValuationNote,
    val metrics: ValuationMetrics = ValuationMetrics(),
    val source: String = "",
    val asOf: LocalDate? = null,
) {
    companion object {
        fun notApplicable(): FundamentalView = FundamentalView(ValuationNote.NOT_APPLICABLE)

        fun missing(): FundamentalView = FundamentalView(ValuationNote.MISSING)

        fun budget(): FundamentalView = FundamentalView(ValuationNote.BUDGET)

        fun ratios(metrics: ValuationMetrics, source: String, asOf: LocalDate): FundamentalView =
            FundamentalView(ValuationNote.RATIOS, metrics, source, asOf)
    }
}
