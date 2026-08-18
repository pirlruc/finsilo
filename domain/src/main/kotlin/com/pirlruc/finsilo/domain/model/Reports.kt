package com.pirlruc.finsilo.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** Mark-to-market holding used by allocation and unrealized PnL. */
data class HoldingValuation(
    val asset: Asset,
    val quantity: BigDecimal,
    val priceEur: BigDecimal?,
    val valueEur: BigDecimal,
    val costEur: BigDecimal,
    val unrealizedPnlEur: BigDecimal,
)

/** One investment-type slice of current NAV, including optional target drift. */
data class AllocationSlice(
    val assetType: AssetType,
    val valueEur: BigDecimal,
    val weightPercent: BigDecimal,
    val targetPercent: BigDecimal?,
    val driftPercent: BigDecimal?,
) {
    val exceedsDriftBand: Boolean
        get() = driftPercent != null && driftPercent.abs() > BigDecimal("5")
}

/** Portfolio allocation at an as-of date. */
data class AllocationReport(
    val asOf: LocalDate,
    val totalValueEur: BigDecimal,
    val unrealizedPnlEur: BigDecimal,
    val cashEur: BigDecimal,
    val slices: List<AllocationSlice>,
    val holdings: List<HoldingValuation>,
)

/** One dense NAV observation. */
data class NavPoint(val date: LocalDate, val valueEur: BigDecimal)

/** Downsampled NAV series for a dashboard range. */
data class HistoryReport(val range: HistoryRange, val from: LocalDate, val to: LocalDate, val points: List<NavPoint>)

/** Rating and SMA context for a held marketable instrument. */
data class MarketSignal(
    val asset: Asset,
    val asOf: LocalDate,
    val rating: AnalystRating,
    val previousRating: AnalystRating?,
    val priceNative: BigDecimal,
    val sma50: BigDecimal?,
    val sma200: BigDecimal?,
    val vsSma50: RelativeToAverage?,
    val vsSma200: RelativeToAverage?,
    val cross: TechnicalCross?,
) {
    val ratingChanged: Boolean
        get() = previousRating != null && previousRating != rating && rating != AnalystRating.NONE
}

/** Combined dashboard payload. */
data class DashboardReport(
    val asOf: LocalDate,
    val allocation: AllocationReport,
    val history: HistoryReport,
    val signals: List<MarketSignal>,
    val twr: TwrReport,
    val yoc: List<YocReport>,
    val warnings: List<String> = emptyList(),
)

/** Time-weighted return and the sub-periods that produced it. */
data class TwrReport(val asOf: LocalDate, val twrPercent: BigDecimal, val subPeriods: List<TwrSubPeriod>)

/** One TWR sub-period. [split] is the event that opened it. */
data class TwrSubPeriod(val from: LocalDate, val to: LocalDate, val returnPercent: BigDecimal, val split: TwrSplit?)

/** Cash-flow events that open a TWR sub-period. */
enum class TwrSplit {
    EXTERNAL_BUY,
    WITHDRAWAL,
}

/** Yield on cost for a dividend-paying holding. */
data class YocReport(
    val asset: Asset,
    val remainingCostEur: BigDecimal,
    val ttmPercent: BigDecimal?,
    val lastTimesFrequencyPercent: BigDecimal?,
    val paymentsPerYear: Int?,
)

/** Kind of realized FIFO line on the Portuguese plus-valias report. */
enum class RealizedKind {
    /** Marketable stock/ETF/crypto/commodity disposal. */
    DISPOSAL,

    /** Locally valued instrument (deposit, CT, unlisted PPR) redemption. */
    REDEMPTION,
}

/** One FIFO lot consumed by a sell in the report year. */
data class RealizedLotLine(
    val asset: Asset,
    val sellDate: LocalDate,
    val acquiredDate: LocalDate,
    val quantity: BigDecimal,
    val costEur: BigDecimal,
    val proceedsEur: BigDecimal,
    val gainEur: BigDecimal,
    val kind: RealizedKind,
)

/** Calendar-year FIFO realized gains in stored EUR. */
data class RealizedGainsReport(val year: Int, val lines: List<RealizedLotLine>, val totalGainEur: BigDecimal)

/** One native-currency close used by parsers and SMA math. */
data class PriceBar(val date: LocalDate, val closeNative: BigDecimal)
