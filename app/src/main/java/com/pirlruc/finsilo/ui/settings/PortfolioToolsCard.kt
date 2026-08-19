package com.pirlruc.finsilo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun PortfolioToolsCard(state: PortfolioToolsUiState, actions: PortfolioToolsActions) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            actions.onPickerBusy(false)
            val bytes = pending
            pending = null
            if (uri != null && bytes != null) writeUri(context, uri, bytes)
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            actions.onPickerBusy(false)
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.openInputStream(uri)?.use { input -> actions.onRestoreBackup(input.readBytes()) }
        }

    fun saveAs(name: String, producer: ((ByteArray) -> Unit) -> Unit) {
        producer { bytes ->
            pending = bytes
            actions.onPickerBusy(true)
            create.launch(name)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TaxExportCard(state, actions, ::saveAs)
        BackupExportCard(state, actions, ::saveAs) {
            actions.onPickerBusy(true)
            open.launch(arrayOf("*/*"))
        }
        if (state.confirmRestore) {
            RestoreConfirmDialog(onConfirm = actions.onConfirmRestore, onCancel = actions.onCancelRestore)
        }
        state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TaxExportCard(
    state: PortfolioToolsUiState,
    actions: PortfolioToolsActions,
    saveAs: (String, ((ByteArray) -> Unit) -> Unit) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Plus-valias report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Calendar-year FIFO in stored EUR. CSV and PDF share the same figures. Redemptions are labeled Resgate. This is not a full IRS pack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state.year,
                actions.onYear,
                label = { Text("Year") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { saveAs("mais-valias-${state.year}.csv", actions.onExportCsv) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export CSV") }
            Button(
                onClick = { saveAs("mais-valias-${state.year}.pdf", actions.onExportPdf) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export PDF") }
        }
    }
}

@Composable
private fun BackupExportCard(
    state: PortfolioToolsUiState,
    actions: PortfolioToolsActions,
    saveAs: (String, ((ByteArray) -> Unit) -> Unit) -> Unit,
    onPickRestore: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Encrypted backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Not plaintext SQLite. Export wraps the file with this install’s recovery code. " +
                    "After unlock on a new phone, type the code from the backup — it can differ " +
                    "from this phone’s lock recovery. Restore asks before overwriting the live " +
                    "ledger, watchlist, templates, and price alerts. The sample portfolio is not a backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state.recovery,
                actions.onRecovery,
                label = { Text("Backup recovery code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { saveAs("finsilo-backup-${LocalDate.now()}.fsi", actions.onExportBackup) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export backup") }
            Button(
                onClick = onPickRestore,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore backup") }
        }
    }
}

private fun writeUri(context: Context, uri: Uri, bytes: ByteArray) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}

@Composable
private fun RestoreConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace live data?") },
        text = {
            Text(
                "Restore overwrites the ledger, watchlist, templates, and price alerts on this device. Encrypted backups on disk are not deleted.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
