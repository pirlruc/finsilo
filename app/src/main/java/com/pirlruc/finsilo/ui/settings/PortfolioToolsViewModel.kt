package com.pirlruc.finsilo.ui.settings

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate
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
)

class PortfolioToolsViewModel(
    private val repository: RoomPortfolioRepository,
    private val lock: AppLockRepository,
    private val gains: GetRealizedGainsUseCase = GetRealizedGainsUseCase(),
) : ViewModel() {
    private val _state = MutableStateFlow(PortfolioToolsUiState())
    val state: StateFlow<PortfolioToolsUiState> = _state.asStateFlow()

    fun setYear(value: String) = _state.update { it.copy(year = value.filter { ch -> ch.isDigit() }.take(4), error = null) }

    fun setRecovery(value: String) = _state.update { it.copy(recovery = value, error = null) }

    fun exportTaxCsv(write: (ByteArray) -> Unit) {
        exportTax(csv = true, write = write)
    }

    fun exportTaxPdf(write: (ByteArray) -> Unit) {
        exportTax(csv = false, write = write)
    }

    fun exportBackup(write: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val recovery = _state.value.recovery
            if (!lock.verifyRecovery(recovery)) {
                _state.update { it.copy(error = "Enter the current recovery code to encrypt the backup.") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = null, status = null) }
            runCatching {
                val snapshot = repository.load()
                withContext(Dispatchers.Default) { LedgerBackupCodec.encrypt(snapshot, recovery) }
            }.onSuccess { bytes ->
                write(bytes)
                _state.update { it.copy(busy = false, status = "Encrypted backup written. Keep the recovery code.") }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "Export failed") }
            }
        }
    }

    fun restoreBackup(bytes: ByteArray) {
        viewModelScope.launch {
            val recovery = _state.value.recovery
            _state.update { it.copy(busy = true, error = null, status = null) }
            val result = withContext(Dispatchers.Default) { LedgerBackupCodec.decrypt(bytes, recovery) }
            when (result) {
                is LedgerBackupResult.Refused ->
                    _state.update { it.copy(busy = false, error = result.reason) }
                is LedgerBackupResult.Restored -> {
                    runCatching { repository.write(result.snapshot) }
                        .onSuccess {
                            _state.update { it.copy(busy = false, status = "Ledger restored from backup.") }
                        }
                        .onFailure { error ->
                            _state.update { it.copy(busy = false, error = error.message ?: "Restore failed") }
                        }
                }
            }
        }
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

/** Printable PDF with the same FIFO figures as [RealizedGainsCsv]. */
object RealizedGainsPdf {
    fun write(report: com.pirlruc.finsilo.domain.model.RealizedGainsReport): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 10f }
        val title = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
        }
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f
        canvas.drawText("Mais-valias FIFO ${report.year}", 40f, y, title)
        y += 24f
        val lines = RealizedGainsCsv.write(report).lineSequence()
        for (line in lines) {
            if (y > 800f) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(line.take(110), 40f, y, paint)
            y += 14f
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
