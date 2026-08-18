package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.time.LocalDate

/** Notification channel for rating changes, SMA crosses, and allocation drift. */
enum class AlertChannel {
    RATING,
    CROSS,
    DRIFT,
}

/** One notification payload built from stored SMAs or allocation drift. */
data class PortfolioAlert(val channel: AlertChannel, val title: String, val body: String)

/**
 * Builds notification payloads from stored SMAs and allocation drift.
 * Live path never invents a golden/death cross.
 */
class GetPortfolioAlertsUseCase(
    private val signals: GetMarketSignalsUseCase = GetMarketSignalsUseCase(),
    private val allocation: GetAllocationUseCase = GetAllocationUseCase(),
) {
    operator fun invoke(snapshot: PortfolioSnapshot, asOf: LocalDate): List<PortfolioAlert> {
        val alerts = ArrayList<PortfolioAlert>()
        for (signal in signals(snapshot, asOf)) {
            if (signal.ratingChanged) {
                alerts +=
                    PortfolioAlert(
                        AlertChannel.RATING,
                        "${signal.asset.symbol} rating",
                        "${checkNotNull(signal.previousRating).displayName} → ${signal.rating.displayName}",
                    )
            }
            when (signal.cross) {
                TechnicalCross.GOLDEN ->
                    alerts += PortfolioAlert(AlertChannel.CROSS, "${signal.asset.symbol} golden cross", "SMA 50 crossed above SMA 200")
                TechnicalCross.DEATH ->
                    alerts += PortfolioAlert(AlertChannel.CROSS, "${signal.asset.symbol} death cross", "SMA 50 crossed below SMA 200")
                null -> Unit
            }
        }
        val drifted = allocation(snapshot, asOf).slices.filter { it.exceedsDriftBand }
        if (drifted.isNotEmpty()) {
            val names = drifted.joinToString { it.assetType.name }
            alerts +=
                PortfolioAlert(
                    AlertChannel.DRIFT,
                    "Allocation drift",
                    "$names is outside the ±${PortfolioValuator.DRIFT_BAND_PERCENT.toPlainString()}% band",
                )
        }
        return alerts
    }
}
