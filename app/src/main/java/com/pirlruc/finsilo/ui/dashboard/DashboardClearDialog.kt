package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ClearSelectionDialog(
    flags: ClearFlags,
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
                    checked = flags.ledger,
                    onChecked = onLedger,
                    title = "Ledger",
                    caption = "Holdings, transactions, quotes, FX, targets, NAV, and price alerts",
                )
                ClearChoice(
                    checked = flags.watchlist,
                    onChecked = onWatchlist,
                    title = "Watchlist",
                    caption = "Followed symbols and their stored quotes",
                )
                ClearChoice(
                    checked = flags.templates,
                    onChecked = onTemplates,
                    title = "Templates",
                    caption = "Saved ledger pre-fills; they never post by themselves",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = flags.ledger || flags.watchlist || flags.templates) {
                Text(if (flags.ledger && flags.watchlist && flags.templates) "Clear all" else "Clear selected")
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
