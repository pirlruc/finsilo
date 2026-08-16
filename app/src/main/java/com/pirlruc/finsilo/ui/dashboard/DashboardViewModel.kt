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
)

class DashboardViewModel(
    private val repository: RoomPortfolioRepository,
    private val getDashboard: GetDashboardUseCase,
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
                    DashboardUiState(loading = false, empty = true, range = _state.value.range)
                } else {
                    val asOf = resolveAsOf(snapshot.marketData.maxOfOrNull { it.date }, snapshot.transactions.maxOfOrNull { it.date })
                    DashboardUiState(
                        loading = false,
                        empty = false,
                        range = _state.value.range,
                        report = getDashboard(snapshot, _state.value.range, asOf),
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
                    return DashboardViewModel(container.repository, container.getDashboard) as T
                }
            }
    }
}
