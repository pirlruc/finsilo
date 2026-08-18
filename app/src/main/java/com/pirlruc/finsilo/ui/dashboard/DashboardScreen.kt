package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.R
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
        onClearLedger = viewModel::setClearLedger,
        onClearWatchlist = viewModel::setClearWatchlist,
        onClearTemplates = viewModel::setClearTemplates,
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
    onClearLedger: (Boolean) -> Unit,
    onClearWatchlist: (Boolean) -> Unit,
    onClearTemplates: (Boolean) -> Unit,
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
        ClearSelectionDialog(
            ledger = state.clearLedger,
            watchlist = state.clearWatchlist,
            templates = state.clearTemplates,
            onLedger = onClearLedger,
            onWatchlist = onClearWatchlist,
            onTemplates = onClearTemplates,
            onConfirm = onConfirmClear,
            onCancel = onCancelClear,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardTopBar(
    empty: Boolean,
    syncing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onShowKey: () -> Unit,
    onSync: () -> Unit,
    onRequestClear: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_silo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text("FinSilo")
            }
        },
        actions = {
            IconButton(onClick = onOpenWatchlist) {
                Icon(Icons.Outlined.Star, contentDescription = "Watchlist")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
            if (!empty) {
                IconButton(onClick = onSync, enabled = !syncing) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Sync quotes")
                }
            }
            DashboardOverflowMenu(onShowKey = onShowKey, onRequestClear = onRequestClear)
        },
    )
}

@Composable
private fun DashboardOverflowMenu(onShowKey: () -> Unit, onRequestClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Alpha Vantage key") },
            onClick = {
                expanded = false
                onShowKey()
            },
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Clear data") },
            onClick = {
                expanded = false
                onRequestClear()
            },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        )
    }
}

@Composable
private fun ClearSelectionDialog(
    ledger: Boolean,
    watchlist: Boolean,
    templates: Boolean,
    onLedger: (Boolean) -> Unit,
    onWatchlist: (Boolean) -> Unit,
    onTemplates: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Clear on this device?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Choose what to wipe. This cannot be undone. Encrypted backups are not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ClearChoice(
                    checked = ledger,
                    onChecked = onLedger,
                    title = "Ledger",
                    caption = "Holdings, transactions, quotes, FX, targets, NAV, and price alerts",
                )
                ClearChoice(
                    checked = watchlist,
                    onChecked = onWatchlist,
                    title = "Watchlist",
                    caption = "Followed symbols and their stored quotes",
                )
                ClearChoice(
                    checked = templates,
                    onChecked = onTemplates,
                    title = "Templates",
                    caption = "Saved ledger pre-fills; they never post by themselves",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = ledger || watchlist || templates) {
                Text(if (ledger && watchlist && templates) "Clear all" else "Clear selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
private fun ClearChoice(checked: Boolean, onChecked: (Boolean) -> Unit, title: String, caption: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
