package com.pirlruc.finsilo.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.usecase.ProbeMarketQuoteUseCase
import com.pirlruc.finsilo.domain.usecase.QuoteProbeResult
import com.pirlruc.finsilo.domain.usecase.SyncWatchlistUseCase
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val loading: Boolean = true,
    val items: List<WatchlistItem> = emptyList(),
    val quotes: Map<String, DailyMarketData> = emptyMap(),
    val ratingPrefs: Map<String, RatingAlertPref> = emptyMap(),
    val symbol: String = "",
    val name: String = "",
    val assetType: AssetType = AssetType.STOCK,
    val currency: Currency = Currency.EUR,
    val status: String? = null,
    val error: String? = null,
    val syncing: Boolean = false,
)

class WatchlistViewModel(
    private val repository: RoomPortfolioRepository,
    private val feed: MarketFeed,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun setSymbol(value: String) = _state.update { it.copy(symbol = value) }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setAssetType(value: AssetType) = _state.update { it.copy(assetType = value) }

    fun setCurrency(value: Currency) = _state.update { it.copy(currency = value) }

    fun add() {
        val symbol = _state.value.symbol.trim()
        if (symbol.isEmpty()) {
            _state.update { it.copy(error = "Symbol is required.") }
            return
        }
        viewModelScope.launch { addItem(symbol) }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            repository.deleteWatchlistItem(id)
            reload()
        }
    }

    fun saveRating(pref: RatingAlertPref) {
        viewModelScope.launch {
            repository.saveRatingAlert(pref)
            reload()
        }
    }

    fun sync() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null, status = "Refreshing watchlist quotes…") }
            runCatching {
                val current = repository.loadWatchlist()
                val prefs = repository.loadRatingAlerts()
                val result = SyncWatchlistUseCase(feed)(current, today(), prefs)
                repository.replaceWatchlistQuotes(result.quotes)
                result
            }.onSuccess { result ->
                val extra = if (result.failures.isEmpty()) "" else " (${result.failures.size} skipped)"
                _state.update { it.copy(syncing = false, status = "Updated ${result.quotes.size} watchlist rows$extra") }
                reload()
            }.onFailure { error ->
                _state.update { it.copy(syncing = false, error = error.message ?: "Watchlist sync failed") }
            }
        }
    }

    private suspend fun addItem(symbol: String) {
        val item =
            WatchlistItem(
                id = UUID.randomUUID().toString(),
                symbol = symbol,
                name = _state.value.name.ifBlank { symbol },
                assetType = _state.value.assetType,
                baseCurrency = _state.value.currency,
            )
        when (val probe = ProbeMarketQuoteUseCase(feed)(item.asFeedAsset(), today())) {
            is QuoteProbeResult.Missing -> {
                _state.update { it.copy(error = probe.reason) }
                return
            }
            is QuoteProbeResult.Found -> {
                repository.saveWatchlistItem(item)
                if (probe.bars.isNotEmpty()) {
                    repository.replaceWatchlistQuotes(QuoteSeed.fromBars(item.id, probe.bars))
                }
            }
        }
        _state.update { it.copy(symbol = "", name = "", error = null) }
        reload()
    }

    private suspend fun reload() {
        val snap = repository.loadWatchlist()
        val prefs =
            repository.loadRatingAlerts()
                .filter { it.scope == RatingAlertScope.WATCHLIST }
                .associateBy { it.targetId }
        _state.update {
            it.copy(
                loading = false,
                items = snap.items,
                quotes = snap.quotes.groupBy { row -> row.assetId }.mapValues { entry -> entry.value.maxBy { row -> row.date } },
                ratingPrefs = prefs,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WatchlistViewModel(container.repository, container.marketFeed) as T
        }
    }
}
