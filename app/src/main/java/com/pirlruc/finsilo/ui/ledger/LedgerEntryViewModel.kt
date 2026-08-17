package com.pirlruc.finsilo.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.parseDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LedgerEntryViewModel(
    private val repository: RoomPortfolioRepository,
    private val record: RecordLedgerEntryUseCase = RecordLedgerEntryUseCase(),
    private val ledger: PositionLedger = PositionLedger(),
) : ViewModel() {
    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()
    private var snapshot: PortfolioSnapshot = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    init {
        viewModelScope.launch { reload() }
    }

    fun prepare() {
        viewModelScope.launch { reload() }
    }

    fun setType(type: TransactionType) {
        _state.update {
            it.copy(
                type = type,
                error = null,
                newInstrument = type == TransactionType.BUY && it.existingAssetId == null,
            )
        }
        refreshHints()
    }

    fun setDate(value: String) {
        _state.update { it.copy(date = value) }
        refreshHints()
    }

    fun setQuantity(value: String) = _state.update { it.copy(quantity = value) }

    fun setUnitPrice(value: String) = _state.update { it.copy(unitPriceNative = value) }

    fun setFees(value: String) = _state.update { it.copy(feesEur = value) }

    fun setEurPerUsd(value: String) = _state.update { it.copy(eurPerUsd = value) }

    fun setExistingAsset(id: String?) {
        _state.update { it.copy(existingAssetId = id, newInstrument = false, error = null) }
        refreshHints()
    }

    fun setNewInstrument(value: Boolean) {
        _state.update { it.copy(newInstrument = value, existingAssetId = if (value) null else it.existingAssetId) }
    }

    fun setSymbol(value: String) = _state.update { it.copy(symbol = value) }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setAssetType(value: AssetType) = _state.update { it.copy(assetType = value) }

    fun setCurrency(value: Currency) = _state.update { it.copy(currency = value) }

    fun setIsin(value: String) = _state.update { it.copy(isin = value) }

    fun setQuoteSymbol(value: String) = _state.update { it.copy(quoteSymbol = value) }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch { persist(onSaved) }
    }

    private suspend fun persist(onSaved: () -> Unit) {
        _state.update { it.copy(saving = true, error = null, status = null) }
        val request = LedgerFormMapper.toRequest(_state.value)
        if (request == null) {
            _state.update { it.copy(saving = false, error = "Fill date, quantity, and price with valid numbers.") }
            return
        }
        when (val result = record(snapshot, request)) {
            is LedgerEntryResult.Rejected -> _state.update { it.copy(saving = false, error = result.reason) }
            is LedgerEntryResult.Accepted -> writeAccepted(result, onSaved)
        }
    }

    private suspend fun writeAccepted(result: LedgerEntryResult.Accepted, onSaved: () -> Unit) {
        runCatching {
            repository.saveLedgerEntry(
                asset = result.asset.takeIf { result.createdAsset },
                transaction = result.transaction,
                fxRate = result.fxRate,
            )
        }.onSuccess {
            _state.update { it.copy(saving = false, status = "Saved ${result.transaction.type.name.lowercase()}") }
            onSaved()
        }.onFailure { error ->
            _state.update { it.copy(saving = false, error = error.message ?: "Could not save") }
        }
    }

    private suspend fun reload() {
        snapshot = repository.load()
        _state.update {
            it.copy(
                loading = false,
                assets = snapshot.assets.filter { asset -> asset.assetType != AssetType.CASH },
                eurPerUsd = it.eurPerUsd.ifBlank {
                    snapshot.fxRates.maxByOrNull { rate -> rate.date }?.eurPerUsd?.toPlainString().orEmpty()
                },
            )
        }
        refreshHints()
    }

    private fun refreshHints() {
        val current = _state.value
        val asOf = parseDate(current.date)
        val prior = if (asOf == null) snapshot.transactions else ledger.transactionsOnOrBefore(snapshot.transactions, asOf)
        val cash = ledger.cashEur(prior, snapshot.assets.associateBy { it.id })
        val asset = snapshot.assets.firstOrNull { it.id == current.existingAssetId }
        val remaining =
            if (asset == null || current.type != TransactionType.SELL) {
                null
            } else {
                ledger.position(prior.filter { it.assetId == asset.id }).quantity.stripTrailingZeros().toPlainString()
            }
        _state.update { it.copy(remainingQty = remaining, cashEur = cash.stripTrailingZeros().toPlainString()) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerEntryViewModel(container.repository) as T
        }
    }
}
