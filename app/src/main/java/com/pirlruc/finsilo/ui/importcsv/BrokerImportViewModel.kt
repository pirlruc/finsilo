package com.pirlruc.finsilo.ui.importcsv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvResult
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolReview
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.usecase.ProbeMarketQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.QuoteProbeResult
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrokerImportUiState(
    val importing: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val drafts: List<ImportSymbolDraft> = emptyList(),
    val reviewing: Boolean = false,
)

class BrokerImportViewModel(
    private val repository: RoomPortfolioRepository,
    private val feed: MarketFeed,
    private val importer: ImportBrokerCsvUseCase = ImportBrokerCsvUseCase(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(BrokerImportUiState())
    val state: StateFlow<BrokerImportUiState> = _state.asStateFlow()
    private var pendingLines: List<BrokerCsvLine> = emptyList()
    private var probeBars: Map<String, List<PriceBar>> = emptyMap()

    fun importCsvs(texts: List<String>, onImported: () -> Unit) {
        viewModelScope.launch { beginReview(texts, onImported) }
    }

    fun setDraftQuote(key: String, quoteSymbol: String) {
        _state.update { current ->
            current.copy(
                drafts = current.drafts.map { draft ->
                    if (draft.key == key) draft.copy(quoteSymbol = quoteSymbol, quoteWarning = null) else draft
                },
            )
        }
    }

    fun confirmImport(onImported: () -> Unit) {
        viewModelScope.launch { finishReview(onImported) }
    }

    fun cancelReview() {
        pendingLines = emptyList()
        probeBars = emptyMap()
        _state.update { it.copy(reviewing = false, drafts = emptyList(), importing = false) }
    }

    private suspend fun beginReview(texts: List<String>, onImported: () -> Unit) {
        _state.update { it.copy(importing = true, error = null, status = null, reviewing = false) }
        val parsed = withContext(Dispatchers.Default) { BrokerCsv.parseAll(texts) }
        if (parsed.error != null) {
            _state.update { it.copy(importing = false, error = parsed.error) }
            return
        }
        pendingLines = parsed.lines
        val probed = probeDrafts(ImportSymbolReview.drafts(parsed.lines))
        if (probed.isEmpty()) {
            persistReviewed(parsed.lines, emptyList(), onImported)
            return
        }
        _state.update { it.copy(importing = false, reviewing = true, drafts = probed) }
    }

    private suspend fun finishReview(onImported: () -> Unit) {
        val drafts = _state.value.drafts
        _state.update { it.copy(importing = true, error = null) }
        val refreshed = probeDrafts(drafts)
        val lines = ImportSymbolReview.apply(pendingLines, refreshed)
        persistReviewed(lines, refreshed, onImported)
    }

    private suspend fun persistReviewed(lines: List<BrokerCsvLine>, drafts: List<ImportSymbolDraft>, onImported: () -> Unit) {
        val loaded =
            runCatching {
                withContext(Dispatchers.Default) {
                    val snapshot = repository.load()
                    snapshot to importer.importLines(snapshot, lines)
                }
            }.getOrElse { error ->
                _state.update { it.copy(importing = false, error = error.message ?: "Import failed") }
                return
            }
        persist(loaded.first, loaded.second, drafts, onImported)
    }

    private suspend fun persist(
        before: PortfolioSnapshot,
        result: ImportBrokerCsvResult,
        drafts: List<ImportSymbolDraft>,
        onImported: () -> Unit,
    ) {
        if (result.error != null) {
            _state.update { it.copy(importing = false, error = result.error, reviewing = false) }
            return
        }
        runCatching {
            repository.persistImport(before, result.snapshot)
            storeProbedQuotes(result.snapshot, drafts)
        }.onSuccess {
            pendingLines = emptyList()
            probeBars = emptyMap()
            _state.update {
                it.copy(importing = false, reviewing = false, drafts = emptyList(), status = importStatus(result, drafts), error = null)
            }
            if (result.accepted > 0) onImported()
        }.onFailure { error ->
            _state.update { it.copy(importing = false, error = error.message ?: "Could not save import") }
        }
    }

    private suspend fun probeDrafts(drafts: List<ImportSymbolDraft>): List<ImportSymbolDraft> {
        val bars = HashMap<String, List<PriceBar>>()
        val probe = ProbeMarketQuoteUseCase(feed)
        val asOf = today()
        val next = drafts.map { draft ->
            if (!draft.needsQuote) return@map draft
            when (val outcome = probe(draft.asProbeAsset(), asOf)) {
                is QuoteProbeResult.Found -> {
                    bars[draft.key] = outcome.bars
                    draft.copy(quoteWarning = null)
                }
                is QuoteProbeResult.Missing -> draft.copy(quoteWarning = outcome.reason)
            }
        }
        probeBars = bars
        return next
    }

    private suspend fun storeProbedQuotes(snapshot: PortfolioSnapshot, drafts: List<ImportSymbolDraft>) {
        drafts.forEach { draft ->
            val bars = probeBars[draft.key].orEmpty()
            if (bars.isEmpty()) return@forEach
            val asset = matchingAsset(snapshot, draft) ?: return@forEach
            repository.upsertQuotes(QuoteSeed.fromBars(asset.id, bars), emptyList())
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BrokerImportViewModel(container.repository, container.marketFeed) as T
        }
    }
}

internal fun matchingAsset(snapshot: PortfolioSnapshot, draft: ImportSymbolDraft): Asset? {
    val isin = draft.isin?.uppercase()
    if (isin != null) {
        snapshot.assets.firstOrNull { it.isin?.uppercase() == isin }?.let { return it }
    }
    return snapshot.assets.firstOrNull { it.symbol.equals(draft.symbol, ignoreCase = true) }
}

internal fun importStatus(result: ImportBrokerCsvResult, drafts: List<ImportSymbolDraft>): String {
    val warned = drafts.filter { it.quoteWarning != null }.map { it.symbol }
    val warning =
        if (warned.isEmpty()) {
            ""
        } else {
            " No live quote for ${warned.take(3).joinToString()}${if (warned.size > 3) "…" else ""}."
        }
    return brokerImportSummary(result) + warning
}

internal fun brokerImportSummary(result: ImportBrokerCsvResult): String {
    val skips =
        if (result.skipped.isEmpty()) {
            ""
        } else {
            " Skipped ${result.skipped.size}: ${result.skipped.take(3).joinToString("; ")}."
        }
    val dups = if (result.duplicates == 0) "" else " ${result.duplicates} duplicate(s)."
    val funded = if (result.fundedDeposits == 0) "" else " ${result.fundedDeposits} cash top-up(s) to fund buys."
    return "Imported ${result.accepted} row(s).$funded$dups$skips"
}
