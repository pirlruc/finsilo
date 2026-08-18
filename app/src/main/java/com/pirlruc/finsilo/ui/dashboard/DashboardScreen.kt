package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    importer: BrokerImportViewModel,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onPickerBusy: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by importer.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        importState = importState,
        onRangeSelected = viewModel::setRange,
        onLoadSample = viewModel::loadSample,
        onRequestClear = viewModel::requestClear,
        onConfirmClear = viewModel::confirmClear,
        onCancelClear = viewModel::cancelClear,
        onSync = viewModel::syncMarketData,
        onSaveKey = viewModel::saveAlphaVantageKey,
        onSaveThreshold = viewModel::saveThreshold,
        onAddTransaction = onAddTransaction,
        onOpenSettings = onOpenSettings,
        onOpenWatchlist = onOpenWatchlist,
        onPickerBusy = onPickerBusy,
        onImportCsvs = { texts -> importer.importCsvs(texts) { viewModel.refresh() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    importState: BrokerImportUiState,
    onRangeSelected: (HistoryRange) -> Unit,
    onLoadSample: () -> Unit,
    onRequestClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onSync: () -> Unit,
    onSaveKey: (String) -> Unit,
    onSaveThreshold: (PriceAlertThreshold) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onPickerBusy: (Boolean) -> Unit,
    onImportCsvs: (List<String>) -> Unit,
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            DashboardTopBar(
                empty = state.empty,
                syncing = state.syncing,
                hasReport = state.report != null,
                onOpenSettings = onOpenSettings,
                onOpenWatchlist = onOpenWatchlist,
                onShowKey = { showKeyDialog = true },
                onSync = onSync,
                onRequestClear = onRequestClear,
            )
        },
        floatingActionButton = {
            if (!state.empty) {
                FloatingActionButton(onClick = onAddTransaction) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error)
                state.empty -> EmptyState(onLoadSample, onAddTransaction, importState, onImportCsvs, onPickerBusy)
                state.report != null ->
                    DashboardContent(
                        report = state.report,
                        range = state.range,
                        statusMessage = state.statusMessage,
                        thresholds = state.thresholds,
                        onRangeSelected = onRangeSelected,
                        onSaveThreshold = onSaveThreshold,
                    )
            }
        }
    }
    if (showKeyDialog) {
        AlphaVantageKeyDialog(
            hasKey = state.hasAlphaVantageKey,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                onSaveKey(key)
                showKeyDialog = false
            },
        )
    }
    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            title = { Text("Clear portfolio?") },
            text = {
                Text(
                    "This permanently deletes holdings, transactions, quotes, FX, targets, and NAV history on this device. Watchlist symbols and ledger templates are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmClear) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = onCancelClear) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardTopBar(
    empty: Boolean,
    syncing: Boolean,
    hasReport: Boolean,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onShowKey: () -> Unit,
    onSync: () -> Unit,
    onRequestClear: () -> Unit,
) {
    TopAppBar(
        title = { Text("FinSilo") },
        actions = {
            IconButton(onClick = onOpenWatchlist) {
                Icon(Icons.Outlined.Star, contentDescription = "Watchlist")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = onShowKey) {
                Icon(Icons.Outlined.Key, contentDescription = "Alpha Vantage key")
            }
            if (!empty) {
                IconButton(onClick = onSync, enabled = !syncing) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync quotes")
                }
            }
            if (hasReport) {
                IconButton(onClick = onRequestClear) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Clear portfolio")
                }
            }
        },
    )
}
