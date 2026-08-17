package com.pirlruc.finsilo.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class HoldingValuation(
    val asset: Asset,
    val quantity: BigDecimal,
    val priceEur: BigDecimal?,
    val valueEur: BigDecimal,
    val costEur: BigDecimal,
    val unrealizedPnlEur: BigDecimal,
)

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

data class AllocationReport(
    val asOf: LocalDate,
    val totalValueEur: BigDecimal,
    val unrealizedPnlEur: BigDecimal,
    val cashEur: BigDecimal,
    val slices: List<AllocationSlice>,
    val holdings: List<HoldingValuation>,
)

data class NavPoint(
    val date: LocalDate,
    val valueEur: BigDecimal,
)

data class HistoryReport(
    val range: HistoryRange,
    val from: LocalDate,
    val to: LocalDate,
    val points: List<NavPoint>,
)

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

data class DashboardReport(
    val asOf: LocalDate,
    val allocation: AllocationReport,
    val history: HistoryReport,
    val signals: List<MarketSignal>,
    val twr: TwrReport,
    val yoc: List<YocReport>,
)

data class TwrReport(
    val asOf: LocalDate,
    val twrPercent: BigDecimal,
    val subPeriods: List<TwrSubPeriod>,
)

data class TwrSubPeriod(
    val from: LocalDate,
    val to: LocalDate,
    val returnPercent: BigDecimal,
    val split: TwrSplit?,
)

enum class TwrSplit {
    EXTERNAL_BUY,
    WITHDRAWAL,
}

data class YocReport(
    val asset: Asset,
    val remainingCostEur: BigDecimal,
    val ttmPercent: BigDecimal?,
    val lastTimesFrequencyPercent: BigDecimal?,
    val paymentsPerYear: Int?,
)

data class PriceBar(
    val date: LocalDate,
    val closeNative: BigDecimal,
)
