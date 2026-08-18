package com.pirlruc.finsilo.ui.dashboard

import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold

internal data class DashboardNavActions(
    val onAddTransaction: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenWatchlist: () -> Unit,
    val onPickerBusy: (Boolean) -> Unit,
    val onImportCsvs: (List<String>) -> Unit,
    val onRangeSelected: (HistoryRange) -> Unit,
    val onSaveThreshold: (PriceAlertThreshold) -> Unit,
    val onSync: () -> Unit,
    val onSaveKey: (String) -> Unit,
    val onLoadSample: () -> Unit,
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
