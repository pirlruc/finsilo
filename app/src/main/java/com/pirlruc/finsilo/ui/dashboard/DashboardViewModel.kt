package com.pirlruc.finsilo.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import com.pirlruc.finsilo.domain.usecase.GetPortfolioHistoryUseCase
import com.pirlruc.finsilo.domain.usecase.PortfolioAlert
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        _state.update { DashboardMutations.requestClear(it) }
    }

    fun cancelClear() {
        _state.update { it.copy(confirmClear = false) }
    }

    fun setClearLedger(value: Boolean) = _state.update { it.copy(clearLedger = value) }

    fun setClearWatchlist(value: Boolean) = _state.update { it.copy(clearWatchlist = value) }

    fun setClearTemplates(value: Boolean) = _state.update { it.copy(clearTemplates = value) }

    fun confirmClear() {
        val selection = DashboardMutations.selection(_state.value)
        if (!selection.any) {
            _state.update { it.copy(confirmClear = false) }
            return
        }
        viewModelScope.launch {
            runCatching { DashboardMutations.applyClear(repository, selection) }
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
        val (hasKey, message) = DashboardMutations.storeAlphaKey(container, key)
        _state.update { it.copy(hasAlphaVantageKey = hasKey, statusMessage = message) }
    }

    fun saveThreshold(threshold: PriceAlertThreshold) {
        viewModelScope.launch {
            runCatching { DashboardMutations.saveThreshold(repository, threshold) }
                .onSuccess { _state.update { DashboardMutations.thresholdSaved(it, threshold) } }
                .onFailure { error ->
                    _state.update { it.copy(statusMessage = error.message ?: "Could not save alert") }
                }
        }
    }

    fun saveRating(pref: RatingAlertPref) {
        viewModelScope.launch {
            runCatching { DashboardMutations.saveRating(repository, pref) }
                .onSuccess { _state.update { DashboardMutations.ratingSaved(it, pref) } }
                .onFailure { error ->
                    _state.update { it.copy(statusMessage = error.message ?: "Could not save rating alert") }
                }
        }
    }

    fun saveInstrument(asset: Asset) {
        viewModelScope.launch {
            runCatching { DashboardMutations.saveInstrument(repository, asset) }
                .onSuccess {
                    _state.update { it.copy(statusMessage = "Instrument updated") }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(statusMessage = error.message ?: "Could not update instrument") }
                }
        }
    }

    fun syncMarketData() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, statusMessage = "Syncing quotes…") }
            runCatching { DashboardMutations.syncQuotes(repository, container, notify, today()) }
                .onSuccess { message ->
                    _state.update { it.copy(syncing = false, statusMessage = message) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(syncing = false, statusMessage = error.message ?: "Sync failed") }
                }
        }
    }

    private suspend fun loadDashboard(): DashboardUiState {
        val (session, state) =
            DashboardLoader.load(
                repository = repository,
                getDashboard = getDashboard,
                range = _state.value.range,
                statusMessage = _state.value.statusMessage,
                hasKey = container.keys.alphaVantageKey() != null,
                today = today(),
            )
        snapshot = session.snapshot
        storedNav = session.storedNav
        asOf = session.asOf
        return state
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = dashboardViewModelFactory(container)
    }
}
