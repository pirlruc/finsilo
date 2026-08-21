package com.pirlruc.finsilo.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import com.pirlruc.finsilo.domain.usecase.ProbeMarketQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.QuoteProbeResult
import com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase
import com.pirlruc.finsilo.domain.usecase.RecordManualQuoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LedgerEntryViewModel(
    private val repository: RoomPortfolioRepository,
    private val feed: MarketFeed? = null,
    private val record: RecordLedgerEntryUseCase = RecordLedgerEntryUseCase(),
    private val ledger: PositionLedger = PositionLedger(),
    private val manualQuote: RecordManualQuoteUseCase = RecordManualQuoteUseCase(),
) : ViewModel() {
    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()
    private var snapshot: PortfolioSnapshot = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    init {
        viewModelScope.launch { reload(resetForm = false) }
    }

    fun prepare() {
        viewModelScope.launch { reload(resetForm = true) }
    }

    fun setType(type: TransactionType) {
        _state.update {
            LedgerFormSessionFactory.withHints(
                snapshot,
                it.copy(
                    type = type,
                    error = null,
                    newInstrument = type == TransactionType.BUY && it.existingAssetId == null,
                ),
                ledger,
            )
        }
    }

    fun setDate(value: String) {
        _state.update { LedgerFormSessionFactory.withHints(snapshot, it.copy(date = value), ledger) }
    }

    fun setQuantity(value: String) = _state.update { it.copy(quantity = value) }

    fun setUnitPrice(value: String) = _state.update { it.copy(unitPriceNative = value) }

    fun setFees(value: String) = _state.update { it.copy(feesEur = value) }

    fun setEurPerUsd(value: String) = _state.update { it.copy(eurPerUsd = value) }

    fun setManualClose(value: String) = _state.update { it.copy(manualClose = value) }

    fun setExistingAsset(id: String?) {
        _state.update {
            LedgerFormSessionFactory.withHints(snapshot, it.copy(existingAssetId = id, newInstrument = false, error = null), ledger)
        }
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

    fun applyTemplate(template: LedgerTemplate) {
        _state.update { LedgerFormSessionFactory.withHints(snapshot, LedgerTemplateActions.apply(it, snapshot, template), ledger) }
    }

    fun saveTemplate(label: String) {
        val current = _state.value
        if (current.type !in LedgerTemplateActions.types) {
            _state.update { it.copy(error = "Templates cover buy, interest, and cash deposit only.") }
            return
        }
        viewModelScope.launch {
            val template = LedgerTemplateActions.draft(current, label)
            runCatching { LedgerTemplateActions.persist(repository, template) }
                .onSuccess {
                    _state.update {
                        it.copy(templates = it.templates + template, status = "Template saved. It did not post a transaction.")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not save template") }
                }
        }
    }

    fun saveManualClose() {
        viewModelScope.launch {
            when (val outcome = LedgerSaveActions.saveManualClose(repository, manualQuote, snapshot, _state.value)) {
                is LedgerSaveOutcome.StateOnly -> _state.value = outcome.state
                is LedgerSaveOutcome.ManualSaved -> {
                    snapshot = outcome.snapshot
                    _state.value = outcome.state
                }
                is LedgerSaveOutcome.Posted -> Unit
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val probe = probeNewBuy()
            if (probe is QuoteProbeResult.Missing) {
                _state.update { it.copy(saving = false, error = probe.reason) }
                return@launch
            }
            val bars = (probe as? QuoteProbeResult.Found)?.bars.orEmpty()
            when (val outcome = LedgerSaveActions.saveEntry(repository, record, snapshot, _state.value)) {
                is LedgerSaveOutcome.StateOnly -> _state.value = outcome.state
                is LedgerSaveOutcome.Posted -> {
                    if (bars.isNotEmpty()) {
                        runCatching { repository.upsertQuotes(QuoteSeed.fromBars(outcome.assetId, bars), emptyList()) }
                    }
                    resetFormFields(outcome.status)
                    onSaved()
                }
                is LedgerSaveOutcome.ManualSaved -> Unit
            }
        }
    }

    private suspend fun probeNewBuy(): QuoteProbeResult? {
        val market = feed ?: return null
        val asset = LedgerQuoteGate.probeAsset(_state.value) ?: return null
        return ProbeMarketQuoteUseCase(market)(asset)
    }

    private suspend fun reload(resetForm: Boolean) {
        val session = LedgerFormSessionFactory.load(repository, _state.value, resetForm)
        snapshot = session.snapshot
        _state.value = LedgerFormSessionFactory.withHints(session.snapshot, session.state, ledger)
    }

    private fun resetFormFields(status: String) {
        _state.value = LedgerFormSessionFactory.reset(snapshot, _state.value, status, ledger)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LedgerEntryViewModel(container.repository, container.marketFeed) as T
        }
    }
}
