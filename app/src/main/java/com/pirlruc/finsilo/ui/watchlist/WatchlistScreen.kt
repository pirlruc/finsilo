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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.ui.theme.label

@Composable
fun WatchlistRoute(viewModel: WatchlistViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WatchlistScreen(
        state = state,
        onClose = onClose,
        onSymbol = viewModel::setSymbol,
        onName = viewModel::setName,
        onAssetType = viewModel::setAssetType,
        onCurrency = viewModel::setCurrency,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onSync = viewModel::sync,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchlistScreen(
    state: WatchlistUiState,
    onClose: () -> Unit,
    onSymbol: (String) -> Unit,
    onName: (String) -> Unit,
    onAssetType: (AssetType) -> Unit,
    onCurrency: (Currency) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onSync: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
            OutlinedTextField(state.symbol, onSymbol, label = { Text("Symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.name, onName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WATCHLIST_TYPES.forEach { type ->
                    FilterChip(
                        selected = state.assetType == type,
                        onClick = { onAssetType(type) },
                        label = { Text(type.label()) },
                    )
                }
            }
            Text("Quote currency", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { currency ->
                    FilterChip(
                        selected = state.currency == currency,
                        onClick = { onCurrency(currency) },
                        label = { Text(currency.name) },
                    )
                }
            }
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add to watchlist") }
            Button(onClick = onSync, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.syncing) "Refreshing…" else "Refresh quotes")
            }
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
                    TextButton(onClick = { onRemove(item.id) }) { Text("Remove") }
                }
            }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private val WATCHLIST_TYPES = listOf(AssetType.STOCK, AssetType.ETF, AssetType.CRYPTO, AssetType.COMMODITY)
