package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(viewModel: DashboardViewModel, onAddTransaction: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onRangeSelected = viewModel::setRange,
        onLoadSample = viewModel::loadSample,
        onClear = viewModel::clearPortfolio,
        onSync = viewModel::syncMarketData,
        onSaveKey = viewModel::saveAlphaVantageKey,
        onAddTransaction = onAddTransaction,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRangeSelected: (HistoryRange) -> Unit,
    onLoadSample: () -> Unit,
    onClear: () -> Unit,
    onSync: () -> Unit,
    onSaveKey: (String) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            DashboardTopBar(
                empty = state.empty,
                syncing = state.syncing,
                hasReport = state.report != null,
                onOpenSettings = onOpenSettings,
                onShowKey = { showKeyDialog = true },
                onSync = onSync,
                onClear = onClear,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(state.error)
                state.empty -> EmptyState(onLoadSample, onAddTransaction)
                state.report != null -> DashboardContent(state.report, state.range, state.statusMessage, onRangeSelected)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardTopBar(
    empty: Boolean,
    syncing: Boolean,
    hasReport: Boolean,
    onOpenSettings: () -> Unit,
    onShowKey: () -> Unit,
    onSync: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = { Text("FinSilo") },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Target allocation")
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
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Clear portfolio")
                }
            }
        },
    )
}
