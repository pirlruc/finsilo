package com.pirlruc.finsilo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.security.AppLockRepository
import com.pirlruc.finsilo.domain.backup.LedgerBackupCodec
import com.pirlruc.finsilo.domain.backup.LedgerBackupResult
import com.pirlruc.finsilo.domain.usecase.GetRealizedGainsUseCase
import com.pirlruc.finsilo.domain.usecase.RealizedGainsCsv
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PortfolioToolsUiState(
    val year: String = LocalDate.now().year.toString(),
    val recovery: String = "",
    val status: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val confirmRestore: Boolean = false,
    val pendingExport: ByteArray? = null,
    val pendingExportName: String? = null,
)

class PortfolioToolsViewModel(
    private val repository: RoomPortfolioRepository,
    private val lock: AppLockRepository,
    private val gains: GetRealizedGainsUseCase = GetRealizedGainsUseCase(),
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(PortfolioToolsUiState())
    val state: StateFlow<PortfolioToolsUiState> = _state.asStateFlow()
    private var pendingRestore: LedgerBackupResult.Restored? = null

    fun setYear(value: String) = _state.update { it.copy(year = value.filter { ch -> ch.isDigit() }.take(4), error = null) }

    fun setRecovery(value: String) = _state.update { it.copy(recovery = value, error = null) }

    fun exportTaxCsv(write: (ByteArray) -> Unit) {
        exportTax(csv = true, write = write)
    }

    fun exportTaxPdf(write: (ByteArray) -> Unit) {
        exportTax(csv = false, write = write)
    }

    fun prepareBackupExport(fileName: String) {
        viewModelScope.launch {
            val recovery = _state.value.recovery
            _state.update { it.copy(busy = true, error = null, status = null, pendingExport = null, pendingExportName = null) }
            val accepted = withContext(cryptoDispatcher) { lock.verifyRecovery(recovery) }
            if (!accepted) {
                _state.update { it.copy(busy = false, error = "Enter the current recovery code to encrypt the backup.") }
                return@launch
            }
            runCatching {
                val snapshot = repository.load()
                val extras = repository.loadBackupExtras()
                withContext(cryptoDispatcher) { LedgerBackupCodec.encrypt(snapshot, recovery, extras) }
            }.onSuccess { bytes ->
                _state.update { it.copy(busy = false, pendingExport = bytes, pendingExportName = fileName) }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "Export failed") }
            }
        }
    }

    fun onExportConsumed(written: Boolean) {
        val status = if (written) "Encrypted backup written. Keep the recovery code." else null
        _state.update { it.copy(pendingExport = null, pendingExportName = null, status = status) }
    }

    fun onExportLaunchFailed(message: String) {
        _state.update {
            it.copy(pendingExport = null, pendingExportName = null, error = message, busy = false)
        }
    }

    fun restoreBackup(bytes: ByteArray) {
        viewModelScope.launch {
            val recovery = _state.value.recovery
            if (recovery.isBlank()) {
                _state.update { it.copy(error = "Enter the backup recovery code to decrypt.") }
                return@launch
            }
            pendingRestore = null
            _state.update { it.copy(busy = true, error = null, status = null, confirmRestore = false) }
            val result = withContext(cryptoDispatcher) { LedgerBackupCodec.decrypt(bytes, recovery) }
            when (result) {
                is LedgerBackupResult.Refused ->
                    _state.update { it.copy(busy = false, error = result.reason) }
                is LedgerBackupResult.Restored -> {
                    pendingRestore = result
                    _state.update { it.copy(busy = false, confirmRestore = true) }
                }
            }
        }
    }

    fun confirmRestore() {
        val pending = pendingRestore
        if (pending == null) {
            _state.update { it.copy(confirmRestore = false, error = "Choose a backup file first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, status = null) }
            runCatching { repository.restoreBackup(pending.snapshot, pending.extras) }
                .onSuccess {
                    pendingRestore = null
                    _state.update {
                        it.copy(busy = false, confirmRestore = false, status = "Ledger, watchlist, templates, and alerts restored.")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message ?: "Restore failed") }
                }
        }
    }

    fun cancelRestore() {
        pendingRestore = null
        _state.update { it.copy(confirmRestore = false) }
    }

    private fun exportTax(csv: Boolean, write: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val year = _state.value.year.toIntOrNull()
            if (year == null) {
                _state.update { it.copy(error = "Enter a calendar year.") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = null, status = null) }
            runCatching {
                val report = gains(repository.load(), year)
                if (csv) {
                    RealizedGainsCsv.write(report).toByteArray(Charsets.UTF_8)
                } else {
                    RealizedGainsPdf.write(report)
                }
            }.onSuccess { bytes ->
                write(bytes)
                _state.update { it.copy(busy = false, status = if (csv) "CSV written." else "PDF written.") }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "Export failed") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PortfolioToolsViewModel(container.repository, container.lockStore) as T
        }
    }
}
