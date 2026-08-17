package com.pirlruc.finsilo.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.LedgerEntryRequest
import com.pirlruc.finsilo.domain.usecase.LedgerEntryResult
import com.pirlruc.finsilo.domain.usecase.NewAssetDraft
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.parseDate
import com.pirlruc.finsilo.domain.usecase.parseDecimal
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LedgerUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val type: TransactionType = TransactionType.BUY,
    val date: String = LocalDate.now().toString(),
    val quantity: String = "",
    val unitPriceNative: String = "",
    val feesEur: String = "0",
    val eurPerUsd: String = "",
    val existingAssetId: String? = null,
    val newInstrument: Boolean = true,
    val symbol: String = "",
    val name: String = "",
    val assetType: AssetType = AssetType.STOCK,
    val currency: Currency = Currency.EUR,
    val isin: String = "",
    val quoteSymbol: String = "",
    val assets: List<Asset> = emptyList(),
    val cashEur: String? = null,
    val remainingQty: String? = null,
)

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
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, status = null) }
            val current = _state.value
            val request = buildRequest(current)
            if (request == null) {
                _state.update { it.copy(saving = false, error = "Fill date, quantity, and price with valid numbers.") }
                return@launch
            }
            when (val result = record(snapshot, request)) {
                is LedgerEntryResult.Rejected ->
                    _state.update { it.copy(saving = false, error = result.reason) }
                is LedgerEntryResult.Accepted -> {
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
            }
        }
    }

    private suspend fun reload() {
        snapshot = repository.load()
        val cash = ledger.cashEur(
            ledger.transactionsOnOrBefore(snapshot.transactions, LocalDate.MAX),
            snapshot.assets.associateBy { it.id },
        )
        _state.update {
            it.copy(
                loading = false,
                assets = snapshot.assets.filter { asset -> asset.assetType != AssetType.CASH },
                cashEur = cash.stripTrailingZeros().toPlainString(),
                eurPerUsd = it.eurPerUsd.ifBlank {
                    snapshot.fxRates.maxByOrNull { rate -> rate.date }?.eurPerUsd?.toPlainString().orEmpty()
                },
            )
        }
        refreshHints()
    }

    private fun refreshHints() {
        val current = _state.value
        val asset = snapshot.assets.firstOrNull { it.id == current.existingAssetId }
        val remaining =
            if (asset == null || current.type != TransactionType.SELL) {
                null
            } else {
                val asOf = parseDate(current.date)
                val prior =
                    if (asOf == null) {
                        snapshot.transactions
                    } else {
                        ledger.transactionsOnOrBefore(snapshot.transactions, asOf)
                    }
                ledger.position(prior.filter { it.assetId == asset.id }).quantity
                    .stripTrailingZeros()
                    .toPlainString()
            }
        _state.update { it.copy(remainingQty = remaining) }
    }

    private fun buildRequest(state: LedgerUiState): LedgerEntryRequest? {
        val date = parseDate(state.date) ?: return null
        val quantity = quantityFor(state) ?: return null
        val price = priceFor(state) ?: return null
        return LedgerEntryRequest(
            type = state.type,
            date = date,
            quantity = quantity,
            unitPriceNative = price,
            feesEur = feesFor(state),
            existingAssetId = existingAssetIdFor(state),
            newAsset = newAssetFor(state),
            eurPerUsd = parseDecimal(state.eurPerUsd),
        )
    }

    private fun quantityFor(state: LedgerUiState): BigDecimal? =
        if (state.type == TransactionType.INTEREST) BigDecimal.ONE else parseDecimal(state.quantity)

    private fun priceFor(state: LedgerUiState): BigDecimal? =
        if (state.type == TransactionType.DEPOSIT_CASH || state.type == TransactionType.WITHDRAWAL) {
            BigDecimal.ONE
        } else {
            parseDecimal(state.unitPriceNative)
        }

    private fun feesFor(state: LedgerUiState): BigDecimal {
        val cashLike = state.type == TransactionType.DEPOSIT_CASH ||
            state.type == TransactionType.WITHDRAWAL ||
            state.type == TransactionType.INTEREST
        return if (cashLike) BigDecimal.ZERO else parseDecimal(state.feesEur) ?: BigDecimal.ZERO
    }

    private fun existingAssetIdFor(state: LedgerUiState): String? =
        if (state.newInstrument && state.type == TransactionType.BUY) null else state.existingAssetId

    private fun newAssetFor(state: LedgerUiState): NewAssetDraft? {
        if (state.type != TransactionType.BUY || !state.newInstrument) return null
        return NewAssetDraft(
            symbol = state.symbol,
            name = state.name,
            assetType = state.assetType,
            baseCurrency = state.currency,
            isin = state.isin.ifBlank { null },
            quoteSymbol = state.quoteSymbol.ifBlank { null },
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerEntryViewModel(container.repository) as T
        }
    }
}
