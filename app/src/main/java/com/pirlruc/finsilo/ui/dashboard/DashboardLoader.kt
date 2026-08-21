package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import java.time.LocalDate

internal object DashboardLoader {
    suspend fun load(
        repository: RoomPortfolioRepository,
        getDashboard: GetDashboardUseCase,
        range: HistoryRange,
        statusMessage: String?,
        hasKey: Boolean,
        today: LocalDate,
    ): DashboardUiState {
        val loaded = repository.loadForDashboard(range, today)
        if (loaded.isEmpty) {
            return DashboardUiState(
                loading = false,
                empty = true,
                range = range,
                hasAlphaVantageKey = hasKey,
            )
        }
        val resolved = resolveAsOf(loaded.marketData.maxOfOrNull { it.date }, loaded.transactions.maxOfOrNull { it.date }, today)
        val storedNav = repository.loadNavHistory()
        val thresholds = repository.loadThresholds().associateBy { it.assetId }
        val ratingPrefs =
            repository.loadRatingAlerts()
                .filter { it.scope == RatingAlertScope.HOLDING }
                .associateBy { it.targetId }
        val counts = loaded.transactions.groupingBy { it.assetId }.eachCount()
        return DashboardUiState(
            loading = false,
            empty = false,
            range = range,
            report = getDashboard(loaded, range, resolved, storedNav),
            statusMessage = statusMessage,
            hasAlphaVantageKey = hasKey,
            thresholds = thresholds,
            ratingPrefs = ratingPrefs,
            transactionsByAsset = counts,
        )
    }

    fun resolveAsOf(lastMarket: LocalDate?, lastTx: LocalDate?, today: LocalDate): LocalDate {
        val observed = listOfNotNull(lastMarket, lastTx).maxOrNull()
        return when {
            observed == null -> today
            observed.isAfter(today) -> observed
            else -> today
        }
    }
}
