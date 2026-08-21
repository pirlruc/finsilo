package com.pirlruc.finsilo.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.ui.alerts.RatingLevelColumn
import com.pirlruc.finsilo.ui.theme.label

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchlistEditor(state: WatchlistUiState, actions: WatchlistActions) {
    OutlinedTextField(
        state.symbol,
        actions.onSymbol,
        label = { Text("Symbol") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state.name,
        actions.onName,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Type", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WATCHLIST_TYPES.forEach { type ->
            FilterChip(
                selected = state.assetType == type,
                onClick = { actions.onAssetType(type) },
                label = { Text(type.label()) },
            )
        }
    }
    Text("Quote currency", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Currency.entries.forEach { currency ->
            FilterChip(
                selected = state.currency == currency,
                onClick = { actions.onCurrency(currency) },
                label = { Text(currency.name) },
            )
        }
    }
    Button(onClick = actions.onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add to watchlist") }
    Button(onClick = actions.onSync, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.syncing) "Refreshing…" else "Refresh quotes")
    }
}

@Composable
private fun WatchlistItems(state: WatchlistUiState, onRemove: (String) -> Unit, onSaveRating: (RatingAlertPref) -> Unit) {
    var alerting by remember { mutableStateOf<WatchlistItem?>(null) }
    state.items.forEach { item ->
        val quote = state.quotes[item.id]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(item.symbol, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.name} · ${item.assetType.label()} · ${item.baseCurrency.name}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (quote != null) {
                    Text(
                        "${quote.closingPriceNative.stripTrailingZeros().toPlainString()} as of ${quote.date}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column {
                TextButton(onClick = { alerting = item }) { Text("Alert") }
                TextButton(onClick = { onRemove(item.id) }) { Text("Remove") }
            }
        }
    }
    val target = alerting
    if (target != null) {
        WatchlistAlertDialog(
            item = target,
            pref = state.ratingPrefs[target.id],
            onDismiss = { alerting = null },
            onSave = { pref ->
                onSaveRating(pref)
                alerting = null
            },
        )
    }
}

@Composable
private fun WatchlistAlertDialog(item: WatchlistItem, pref: RatingAlertPref?, onDismiss: () -> Unit, onSave: (RatingAlertPref) -> Unit) {
    var levels by remember { mutableStateOf(RatingAlertPref.effective(pref, RatingAlertScope.WATCHLIST)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.symbol} rating alerts") },
        text = {
            RatingLevelColumn(levels) { rating, checked ->
                levels = if (checked) levels + rating else levels - rating
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(RatingAlertPref(item.id, RatingAlertScope.WATCHLIST, levels)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val WATCHLIST_TYPES = listOf(AssetType.STOCK, AssetType.ETF, AssetType.CRYPTO, AssetType.COMMODITY)
