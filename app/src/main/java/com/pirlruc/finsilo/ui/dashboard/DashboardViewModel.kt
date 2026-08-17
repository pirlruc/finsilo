package com.pirlruc.finsilo.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val error: String? = null,
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    val report: DashboardReport? = null,
    val syncing: Boolean = false,
    val statusMessage: String? = null,
    val hasAlphaVantageKey: Boolean = false,
)

class DashboardViewModel(
    private val repository: RoomPortfolioRepository,
    private val getDashboard: GetDashboardUseCase,
    private val container: AppContainer,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val snapshot = repository.load()
                if (snapshot.isEmpty) {
                    DashboardUiState(
                        loading = false,
                        empty = true,
                        range = _state.value.range,
                        hasAlphaVantageKey = container.keys.alphaVantageKey() != null,
                    )
                } else {
                    val asOf = resolveAsOf(snapshot.marketData.maxOfOrNull { it.date }, snapshot.transactions.maxOfOrNull { it.date })
                    DashboardUiState(
                        loading = false,
                        empty = false,
                        range = _state.value.range,
                        report = getDashboard(snapshot, _state.value.range, asOf),
                        statusMessage = _state.value.statusMessage,
                        hasAlphaVantageKey = container.keys.alphaVantageKey() != null,
                    )
                }
            }.onSuccess { next -> _state.value = next }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    fun setRange(range: HistoryRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        refresh()
    }

    fun loadSample() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.write(SamplePortfolioFactory.create(today())) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    fun clearPortfolio() {
        viewModelScope.launch {
            runCatching { repository.clear() }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    fun saveAlphaVantageKey(key: String) {
        container.keys.setAlphaVantageKey(key)
        _state.update {
            it.copy(
                hasAlphaVantageKey = key.isNotBlank(),
                statusMessage = if (key.isBlank()) "Alpha Vantage key cleared" else "Alpha Vantage key stored on-device",
            )
        }
    }

    fun syncMarketData() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, statusMessage = "Syncing quotes…") }
            runCatching {
                val snapshot = repository.load()
                val asOf = today()
                val fx = runCatching {
                    com.pirlruc.finsilo.domain.model.CurrencyRate(asOf, container.marketFeed.eurPerUsd())
                }.getOrNull()
                val result = com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase(container.marketFeed)(snapshot, asOf)
                repository.upsertQuotes(result.marketData, fx)
                result
            }.onSuccess { result ->
                val extra = if (result.failures.isEmpty()) "" else " (${result.failures.size} skipped)"
                _state.update { it.copy(syncing = false, statusMessage = "Updated ${result.marketData.size} daily rows$extra") }
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(syncing = false, statusMessage = error.message ?: "Sync failed") }
            }
        }
    }

    private fun resolveAsOf(lastMarket: LocalDate?, lastTx: LocalDate?): LocalDate {
        val observed = listOfNotNull(lastMarket, lastTx).maxOrNull()
        val todayDate = today()
        return when {
            observed == null -> todayDate
            observed.isAfter(todayDate) -> observed
            else -> todayDate
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(container.repository, container.getDashboard, container) as T
                }
            }
    }
}
