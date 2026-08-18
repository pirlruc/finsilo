package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        nav = DashboardNavActions(
            onAddTransaction = onAddTransaction,
            onOpenSettings = onOpenSettings,
            onOpenWatchlist = onOpenWatchlist,
            onPickerBusy = onPickerBusy,
            onImportCsvs = { texts -> importer.importCsvs(texts) { viewModel.refresh() } },
            onRangeSelected = viewModel::setRange,
            onSaveThreshold = viewModel::saveThreshold,
            onSync = viewModel::syncMarketData,
            onSaveKey = viewModel::saveAlphaVantageKey,
            onLoadSample = viewModel::loadSample,
        ),
        clear = DashboardClearActions(
            onRequestClear = viewModel::requestClear,
            onConfirmClear = viewModel::confirmClear,
            onCancelClear = viewModel::cancelClear,
            onClearLedger = viewModel::setClearLedger,
            onClearWatchlist = viewModel::setClearWatchlist,
            onClearTemplates = viewModel::setClearTemplates,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    importState: BrokerImportUiState,
    nav: DashboardNavActions,
    clear: DashboardClearActions,
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            DashboardTopBar(
                empty = state.empty,
                syncing = state.syncing,
                onOpenSettings = nav.onOpenSettings,
                onOpenWatchlist = nav.onOpenWatchlist,
                onShowKey = { showKeyDialog = true },
                onSync = nav.onSync,
                onRequestClear = clear.onRequestClear,
            )
        },
        floatingActionButton = {
            if (!state.empty) {
                FloatingActionButton(onClick = nav.onAddTransaction) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error)
                state.empty ->
                    EmptyState(nav.onLoadSample, nav.onAddTransaction, importState, nav.onImportCsvs, nav.onPickerBusy)
                state.report != null ->
                    DashboardContent(
                        report = state.report,
                        range = state.range,
                        statusMessage = state.statusMessage,
                        thresholds = state.thresholds,
                        onRangeSelected = nav.onRangeSelected,
                        onSaveThreshold = nav.onSaveThreshold,
                    )
            }
        }
    }
    if (showKeyDialog) {
        AlphaVantageKeyDialog(
            hasKey = state.hasAlphaVantageKey,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                nav.onSaveKey(key)
                showKeyDialog = false
            },
        )
    }
    if (state.confirmClear) {
        ClearSelectionDialog(
            flags = ClearFlags(state.clearLedger, state.clearWatchlist, state.clearTemplates),
            onLedger = clear.onClearLedger,
            onWatchlist = clear.onClearWatchlist,
            onTemplates = clear.onClearTemplates,
            onConfirm = clear.onConfirmClear,
            onCancel = clear.onCancelClear,
        )
    }
}
