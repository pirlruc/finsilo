package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

/** Notification channel for rating changes, SMA crosses, and allocation drift. */
enum class AlertChannel {
    RATING,
    CROSS,
    DRIFT,
    THRESHOLD,
}

/** One notification payload built from stored SMAs or allocation drift. */
data class PortfolioAlert(val channel: AlertChannel, val title: String, val body: String)

/**
 * Builds notification payloads from stored SMAs, rating prefs, and allocation drift.
 * Live path never invents a golden/death cross.
 */
class GetPortfolioAlertsUseCase(
    private val signals: GetMarketSignalsUseCase = GetMarketSignalsUseCase(),
    private val allocation: GetAllocationUseCase = GetAllocationUseCase(),
    private val ratings: GetRatingAlertsUseCase = GetRatingAlertsUseCase(),
) {
    operator fun invoke(
        snapshot: PortfolioSnapshot,
        asOf: LocalDate,
        watchlist: WatchlistSnapshot = WatchlistSnapshot(),
        ratingPrefs: List<RatingAlertPref> = emptyList(),
    ): List<PortfolioAlert> {
        val signalRows = signals(snapshot, asOf)
        return crossAlerts(signalRows) +
            driftAlerts(snapshot, asOf) +
            ratings(signalRows, watchlist.items, watchlist.quotes, ratingPrefs, asOf)
    }

    private fun crossAlerts(signalRows: List<com.pirlruc.finsilo.domain.model.MarketSignal>): List<PortfolioAlert> =
        signalRows.mapNotNull { signal ->
            when (signal.cross) {
                TechnicalCross.GOLDEN ->
                    PortfolioAlert(AlertChannel.CROSS, "${signal.asset.symbol} golden cross", "SMA 50 crossed above SMA 200")
                TechnicalCross.DEATH ->
                    PortfolioAlert(AlertChannel.CROSS, "${signal.asset.symbol} death cross", "SMA 50 crossed below SMA 200")
                null -> null
            }
        }

    private fun driftAlerts(snapshot: PortfolioSnapshot, asOf: LocalDate): List<PortfolioAlert> {
        val drifted = allocation(snapshot, asOf).slices.filter { it.exceedsDriftBand }
        if (drifted.isEmpty()) return emptyList()
        val names = drifted.joinToString { it.assetType.name }
        return listOf(
            PortfolioAlert(
                AlertChannel.DRIFT,
                "Allocation drift",
                "$names is outside the ±${PortfolioValuator.DRIFT_BAND_PERCENT.toPlainString()}% band",
            ),
        )
    }
}
