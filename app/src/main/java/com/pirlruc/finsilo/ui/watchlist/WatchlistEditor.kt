package com.pirlruc.finsilo.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.ui.theme.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WatchlistEditor(state: WatchlistUiState, actions: WatchlistActions) {
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

private val WATCHLIST_TYPES = listOf(AssetType.STOCK, AssetType.ETF, AssetType.CRYPTO, AssetType.COMMODITY)
