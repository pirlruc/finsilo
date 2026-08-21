package com.pirlruc.finsilo.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.ui.alerts.RatingLevelColumn
import com.pirlruc.finsilo.ui.theme.label

@Composable
internal fun WatchlistItems(state: WatchlistUiState, onRemove: (String) -> Unit, onSaveRating: (RatingAlertPref) -> Unit) {
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
