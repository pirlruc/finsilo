package com.pirlruc.finsilo.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.sync.PortfolioAlertNotifier
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import com.pirlruc.finsilo.domain.usecase.GetPortfolioHistoryUseCase
import com.pirlruc.finsilo.domain.usecase.GetPriceThresholdAlertsUseCase
import com.pirlruc.finsilo.domain.usecase.PortfolioAlert
import com.pirlruc.finsilo.domain.usecase.SyncMarketDataUseCase
import java.time.LocalDate
import kotlinx.coroutines.Job
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
    val confirmClear: Boolean = false,
    val thresholds: Map<String, PriceAlertThreshold> = emptyMap(),
    val transactionsByAsset: Map<String, Int> = emptyMap(),
)

class DashboardViewModel(
    private val repository: RoomPortfolioRepository,
    private val getDashboard: GetDashboardUseCase,
    private val container: AppContainer,
    private val notify: (List<PortfolioAlert>) -> Unit = {},
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    private var snapshot: PortfolioSnapshot? = null
    private var storedNav: List<NavPoint> = emptyList()
    private var asOf: LocalDate? = null
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val hadReport = _state.value.report != null
                _state.update { it.copy(loading = !hadReport, error = null) }
                runCatching { loadDashboard() }
                    .onSuccess { next -> _state.value = next }
                    .onFailure { error ->
                        _state.update { it.copy(loading = false, error = error.message ?: error.javaClass.simpleName) }
                    }
            }
    }

    fun setRange(range: HistoryRange) {
        if (range == _state.value.range) return
        val snap = snapshot
        val date = asOf
        val report = _state.value.report
        if (snap == null || date == null || report == null) {
            _state.update { it.copy(range = range) }
            refresh()
            return
        }
        val history = GetPortfolioHistoryUseCase()(snap, range, date, storedNav)
        _state.update { it.copy(range = range, report = report.copy(history = history)) }
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

    fun requestClear() {
        _state.update { it.copy(confirmClear = true) }
    }

    fun cancelClear() {
        _state.update { it.copy(confirmClear = false) }
    }

    fun confirmClear() {
        viewModelScope.launch {
            runCatching { repository.clear() }
                .onSuccess {
                    snapshot = null
                    storedNav = emptyList()
                    asOf = null
                    _state.update { it.copy(confirmClear = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(confirmClear = false, error = error.message ?: error.javaClass.simpleName) }
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

    fun saveThreshold(threshold: PriceAlertThreshold) {
        viewModelScope.launch {
            runCatching { repository.saveThreshold(threshold) }
                .onSuccess {
                    val next = _state.value.thresholds.toMutableMap()
                    if (threshold.isEmpty) next.remove(threshold.assetId) else next[threshold.assetId] = threshold
                    _state.update { it.copy(thresholds = next, statusMessage = "Price alert saved") }
                }
                .onFailure { error ->
                    _state.update { it.copy(statusMessage = error.message ?: "Could not save alert") }
                }
        }
    }

    fun syncMarketData() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, statusMessage = "Syncing quotes…") }
            runCatching {
                val loaded = repository.load()
                val day = today()
                val result = SyncMarketDataUseCase(container.marketFeed)(loaded, day)
                repository.upsertQuotes(result.marketData, result.fxRates)
                val updated = repository.load()
                val alerts =
                    GetPriceThresholdAlertsUseCase()(updated, repository.loadThresholds(), day)
                notify(alerts)
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

    private suspend fun loadDashboard(): DashboardUiState {
        val loaded = repository.load()
        snapshot = loaded
        if (loaded.isEmpty) {
            return DashboardUiState(
                loading = false,
                empty = true,
                range = _state.value.range,
                hasAlphaVantageKey = container.keys.alphaVantageKey() != null,
            )
        }
        val resolved = resolveAsOf(loaded.marketData.maxOfOrNull { it.date }, loaded.transactions.maxOfOrNull { it.date })
        asOf = resolved
        repository.rebuildNavHistoryIfNeeded(loaded, resolved)
        storedNav = repository.loadNavHistory()
        val thresholds = repository.loadThresholds().associateBy { it.assetId }
        val counts = loaded.transactions.groupingBy { it.assetId }.eachCount()
        return DashboardUiState(
            loading = false,
            empty = false,
            range = _state.value.range,
            report = getDashboard(loaded, _state.value.range, resolved, storedNav),
            statusMessage = _state.value.statusMessage,
            hasAlphaVantageKey = container.keys.alphaVantageKey() != null,
            thresholds = thresholds,
            transactionsByAsset = counts,
        )
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
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val notifier = PortfolioAlertNotifier(container.application)
                return DashboardViewModel(
                    container.repository,
                    container.getDashboard,
                    container,
                    notify = { notifier.publish(it) },
                ) as T
            }
        }
    }
}
