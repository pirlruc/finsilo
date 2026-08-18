package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import java.time.LocalDate

internal data class DashboardSession(val snapshot: PortfolioSnapshot, val storedNav: List<NavPoint>, val asOf: LocalDate?)

internal object DashboardLoader {
    suspend fun load(
        repository: RoomPortfolioRepository,
        getDashboard: GetDashboardUseCase,
        range: HistoryRange,
        statusMessage: String?,
        hasKey: Boolean,
        today: LocalDate,
    ): Pair<DashboardSession, DashboardUiState> {
        val loaded = repository.load()
        if (loaded.isEmpty) {
            return DashboardSession(loaded, emptyList(), null) to
                DashboardUiState(
                    loading = false,
                    empty = true,
                    range = range,
                    hasAlphaVantageKey = hasKey,
                )
        }
        val resolved = resolveAsOf(loaded.marketData.maxOfOrNull { it.date }, loaded.transactions.maxOfOrNull { it.date }, today)
        repository.rebuildNavHistoryIfNeeded(loaded, resolved)
        val storedNav = repository.loadNavHistory()
        val thresholds = repository.loadThresholds().associateBy { it.assetId }
        val counts = loaded.transactions.groupingBy { it.assetId }.eachCount()
        val state =
            DashboardUiState(
                loading = false,
                empty = false,
                range = range,
                report = getDashboard(loaded, range, resolved, storedNav),
                statusMessage = statusMessage,
                hasAlphaVantageKey = hasKey,
                thresholds = thresholds,
                transactionsByAsset = counts,
            )
        return DashboardSession(loaded, storedNav, resolved) to state
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
