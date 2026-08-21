package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.ClearSelection
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.market.QuoteMerge
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.usecase.GetPortfolioAlertsUseCase
import com.pirlruc.finsilo.domain.usecase.GetPriceThresholdAlertsUseCase
import com.pirlruc.finsilo.domain.usecase.PortfolioAlert
import com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase
import com.pirlruc.finsilo.domain.usecase.SyncWatchlistUseCase
import java.time.LocalDate

internal object DashboardMutations {
    fun requestClear(state: DashboardUiState): DashboardUiState = state.copy(
        confirmClear = true,
        clearLedger = true,
        clearWatchlist = true,
        clearTemplates = true,
    )

    fun selection(state: DashboardUiState): ClearSelection = ClearSelection(
        ledger = state.clearLedger,
        watchlist = state.clearWatchlist,
        templates = state.clearTemplates,
    )

    suspend fun applyClear(repository: RoomPortfolioRepository, selection: ClearSelection) {
        repository.applyClear(selection)
    }

    fun storeAlphaKey(container: AppContainer, key: String): Pair<Boolean, String> {
        container.keys.setAlphaVantageKey(key)
        val message = if (key.isBlank()) "Alpha Vantage key cleared" else "Alpha Vantage key stored on-device"
        return key.isNotBlank() to message
    }

    suspend fun saveThreshold(repository: RoomPortfolioRepository, threshold: PriceAlertThreshold) {
        repository.saveThreshold(threshold)
    }

    suspend fun saveRating(repository: RoomPortfolioRepository, pref: RatingAlertPref) {
        repository.saveRatingAlert(pref)
    }

    suspend fun saveInstrument(repository: RoomPortfolioRepository, asset: Asset) {
        repository.upsertAsset(asset)
    }

    fun thresholdSaved(state: DashboardUiState, threshold: PriceAlertThreshold): DashboardUiState {
        val next = state.thresholds.toMutableMap()
        if (threshold.isEmpty) next.remove(threshold.assetId) else next[threshold.assetId] = threshold
        return state.copy(thresholds = next, statusMessage = "Price alert saved")
    }

    fun ratingSaved(state: DashboardUiState, pref: RatingAlertPref): DashboardUiState {
        val next = state.ratingPrefs.toMutableMap()
        next[pref.targetId] = pref
        return state.copy(ratingPrefs = next, statusMessage = "Rating alert saved")
    }

    suspend fun syncQuotes(
        repository: RoomPortfolioRepository,
        container: AppContainer,
        notify: (List<PortfolioAlert>) -> Unit,
        today: LocalDate,
    ): String {
        val loaded = repository.load()
        val result = SyncMarketDataUseCase(container.marketFeed)(loaded, today)
        repository.upsertQuotes(result.marketData, result.fxRates)
        val prefs = repository.loadRatingAlerts()
        val watch = repository.loadWatchlist()
        val watched = SyncWatchlistUseCase(container.marketFeed)(watch, today, prefs)
        repository.replaceWatchlistQuotes(watched.quotes)
        val updated =
            loaded.copy(
                marketData = QuoteMerge.market(loaded.marketData, result.marketData),
                fxRates = QuoteMerge.fx(loaded.fxRates, result.fxRates),
            )
        val alerts =
            GetPortfolioAlertsUseCase()(updated, today, watch.copy(quotes = watched.quotes), prefs) +
                GetPriceThresholdAlertsUseCase()(updated, repository.loadThresholds(), today)
        notify(alerts)
        val skipped = result.failures.size + watched.failures.size
        val extra = if (skipped == 0) "" else " ($skipped skipped)"
        return "Updated ${result.marketData.size} daily rows$extra"
    }
}
