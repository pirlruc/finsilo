package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref

internal data class ImportNavActions(
    val onImportCsvs: (List<String>) -> Unit,
    val onQuoteSymbol: (String, String) -> Unit,
    val onConfirmReview: () -> Unit,
    val onCancelReview: () -> Unit,
    val onPickerBusy: (Boolean) -> Unit,
)

internal data class DashboardNavActions(
    val onAddTransaction: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenWatchlist: () -> Unit,
    val import: ImportNavActions,
    val onRangeSelected: (HistoryRange) -> Unit,
    val onSaveThreshold: (PriceAlertThreshold) -> Unit,
    val onSaveRating: (RatingAlertPref) -> Unit,
    val onSaveInstrument: (Asset) -> Unit,
    val onSync: () -> Unit,
    val onSaveKey: (String) -> Unit,
    val onLoadSample: () -> Unit,
)

internal data class HoldingActions(
    val onSaveThreshold: (PriceAlertThreshold) -> Unit,
    val onSaveRating: (RatingAlertPref) -> Unit,
    val onSaveInstrument: (Asset) -> Unit,
)

internal data class DashboardClearActions(
    val onRequestClear: () -> Unit,
    val onConfirmClear: () -> Unit,
    val onCancelClear: () -> Unit,
    val onClearLedger: (Boolean) -> Unit,
    val onClearWatchlist: (Boolean) -> Unit,
    val onClearTemplates: (Boolean) -> Unit,
)

internal data class ClearFlags(val ledger: Boolean, val watchlist: Boolean, val templates: Boolean)
