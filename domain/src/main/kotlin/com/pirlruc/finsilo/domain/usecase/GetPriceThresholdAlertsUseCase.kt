package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.HUNDRED
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.div
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Evaluates persisted EUR and percent thresholds on stored bars only.
 * Does not call a market feed.
 */
class GetPriceThresholdAlertsUseCase(private val ledger: PositionLedger = PositionLedger()) {
    operator fun invoke(snapshot: PortfolioSnapshot, thresholds: List<PriceAlertThreshold>, asOf: LocalDate): List<PortfolioAlert> {
        if (thresholds.isEmpty()) return emptyList()
        val assets = snapshot.assets.associateBy { it.id }
        val market = ledger.indexMarket(snapshot.marketData)
        val fx = ledger.eurPerUsdOn(asOf, snapshot.fxRates)
        val alerts = ArrayList<PortfolioAlert>()
        for (threshold in thresholds) {
            val asset = assets[threshold.assetId] ?: continue
            alerts += alertsFor(asset, threshold, market[asset.id].orEmpty(), fx, asOf)
        }
        return alerts
    }

    private fun alertsFor(
        asset: Asset,
        threshold: PriceAlertThreshold,
        bars: List<DailyMarketData>,
        fx: BigDecimal?,
        asOf: LocalDate,
    ): List<PortfolioAlert> {
        val usable = bars.filter { !it.date.isAfter(asOf) }
        if (usable.size < 2) return emptyList()
        val today = usable[usable.lastIndex]
        val yesterday = usable[usable.lastIndex - 1]
        val todayEur = eurPrice(asset, today, fx) ?: return emptyList()
        val yesterdayEur = eurPrice(asset, yesterday, fx) ?: return emptyList()
        return buildAlerts(asset, threshold, todayEur, yesterdayEur)
    }

    private fun buildAlerts(
        asset: Asset,
        threshold: PriceAlertThreshold,
        todayEur: BigDecimal,
        yesterdayEur: BigDecimal,
    ): List<PortfolioAlert> {
        val alerts = ArrayList<PortfolioAlert>(2)
        val percent = threshold.percentMove
        if (percent != null && yesterdayEur.signum() != 0) {
            val move = times(div(minus(todayEur, yesterdayEur), yesterdayEur), HUNDRED)
            if (move.abs().compareTo(percent) >= 0) {
                alerts +=
                    PortfolioAlert(
                        AlertChannel.THRESHOLD,
                        "${asset.symbol} moved ${move.stripTrailingZeros().toPlainString()}%",
                        "Day change reached the ${percent.stripTrailingZeros().toPlainString()}% threshold.",
                    )
            }
        }
        val level = threshold.eurLevel ?: return alerts
        if (crossed(yesterdayEur, todayEur, level)) {
            alerts +=
                PortfolioAlert(
                    AlertChannel.THRESHOLD,
                    "${asset.symbol} crossed ${level.stripTrailingZeros().toPlainString()} EUR",
                    "Last EUR quote moved across the saved level.",
                )
        }
        return alerts
    }

    private fun eurPrice(asset: Asset, bar: DailyMarketData, fx: BigDecimal?): BigDecimal? {
        val currency = QuoteCurrency.of(asset)
        val rate = if (currency == Currency.EUR) BigDecimal.ONE else fx
        return rate?.let { toEur(bar.closingPriceNative, currency, it) }
    }

    private fun crossed(previous: BigDecimal, current: BigDecimal, level: BigDecimal): Boolean {
        if (previous.compareTo(current) == 0) return false
        val before = previous.compareTo(level)
        val after = current.compareTo(level)
        return before == 0 || after == 0 || (before < 0) != (after < 0)
    }
}
