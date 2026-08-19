package com.pirlruc.finsilo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.usecase.SaveTargetAllocationUseCase
import com.pirlruc.finsilo.domain.usecase.TargetAllocationResult
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TargetSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val weights: Map<AssetType, String> = AssetType.entries.associateWith { "0" },
    val sum: String = "0",
    val error: String? = null,
    val status: String? = null,
)

class TargetSettingsViewModel(
    private val repository: RoomPortfolioRepository,
    private val saveTargets: SaveTargetAllocationUseCase = SaveTargetAllocationUseCase(),
) : ViewModel() {
    private val _state = MutableStateFlow(TargetSettingsUiState())
    val state: StateFlow<TargetSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = repository.load()
            val stored = snapshot.targets.associate { it.assetType to it.weightPercent.stripTrailingZeros().toPlainString() }
            val weights = AssetType.entries.associateWith { stored[it] ?: "0" }
            _state.value = TargetSettingsUiState(loading = false, weights = weights, sum = sumOf(weights))
        }
    }

    fun setWeight(type: AssetType, value: String) {
        val next = _state.value.weights.toMutableMap().apply { put(type, value) }
        _state.update { it.copy(weights = next, sum = sumOf(next), error = null, status = null) }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val parsed = HashMap<AssetType, BigDecimal>()
            for ((type, raw) in _state.value.weights) {
                val value = parseDecimal(raw) ?: BigDecimal.ZERO
                parsed[type] = value
            }
            when (val result = saveTargets(parsed)) {
                is TargetAllocationResult.Rejected ->
                    _state.update { it.copy(saving = false, error = result.reason) }
                is TargetAllocationResult.Accepted -> {
                    runCatching { repository.replaceTargets(result.targets) }
                        .onSuccess {
                            _state.update { it.copy(saving = false, status = "Targets saved") }
                            onSaved()
                        }
                        .onFailure { error ->
                            _state.update { it.copy(saving = false, error = error.message ?: "Could not save") }
                        }
                }
            }
        }
    }

    private fun sumOf(weights: Map<AssetType, String>): String {
        val total = weights.values.fold(BigDecimal.ZERO) { acc, raw ->
            acc.add(parseDecimal(raw) ?: BigDecimal.ZERO)
        }
        return total.stripTrailingZeros().toPlainString()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TargetSettingsViewModel(container.repository) as T
        }
    }
}
