package com.pirlruc.finsilo.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WatchlistRoute(viewModel: WatchlistViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WatchlistScreen(
        state = state,
        actions = WatchlistActions(
            onClose = onClose,
            onSymbol = viewModel::setSymbol,
            onName = viewModel::setName,
            onAssetType = viewModel::setAssetType,
            onCurrency = viewModel::setCurrency,
            onAdd = viewModel::add,
            onRemove = viewModel::remove,
            onSync = viewModel::sync,
            onSaveRating = viewModel::saveRating,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchlistScreen(state: WatchlistUiState, actions: WatchlistActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Followed symbols are stored separately from the live ledger. They never change FIFO, TWR, or the allocation pie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WatchlistEditor(state, actions)
            WatchlistItems(state, actions.onRemove, actions.onSaveRating)
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
