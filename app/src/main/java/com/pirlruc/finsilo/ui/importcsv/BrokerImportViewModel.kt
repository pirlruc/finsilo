package com.pirlruc.finsilo.ui.importcsv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvResult
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrokerImportUiState(val importing: Boolean = false, val status: String? = null, val error: String? = null)

class BrokerImportViewModel(
    private val repository: RoomPortfolioRepository,
    private val importer: ImportBrokerCsvUseCase = ImportBrokerCsvUseCase(),
) : ViewModel() {
    private val _state = MutableStateFlow(BrokerImportUiState())
    val state: StateFlow<BrokerImportUiState> = _state.asStateFlow()

    fun importCsvs(texts: List<String>, onImported: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null, status = null) }
            val result =
                runCatching {
                    withContext(Dispatchers.Default) {
                        val snapshot = repository.load()
                        importer(snapshot, texts)
                    }
                }.getOrElse { error ->
                    _state.update { it.copy(importing = false, error = error.message ?: "Import failed") }
                    return@launch
                }
            persist(result, onImported)
        }
    }

    private suspend fun persist(result: ImportBrokerCsvResult, onImported: () -> Unit) {
        if (result.error != null) {
            _state.update { it.copy(importing = false, error = result.error) }
            return
        }
        runCatching { repository.write(result.snapshot) }
            .onSuccess {
                _state.update { it.copy(importing = false, status = summary(result), error = null) }
                if (result.accepted > 0) onImported()
            }
            .onFailure { error ->
                _state.update { it.copy(importing = false, error = error.message ?: "Could not save import") }
            }
    }

    private fun summary(result: ImportBrokerCsvResult): String {
        val skips = if (result.skipped.isEmpty()) "" else " Skipped ${result.skipped.size}."
        val dups = if (result.duplicates == 0) "" else " ${result.duplicates} duplicate(s)."
        val funded = if (result.fundedDeposits == 0) "" else " ${result.fundedDeposits} cash top-up(s) to fund buys."
        return "Imported ${result.accepted} row(s).$funded$dups$skips"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BrokerImportViewModel(container.repository) as T
        }
    }
}
