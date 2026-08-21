package com.pirlruc.finsilo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.runtime.LaunchedEffect
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
    val create = rememberBackupCreateLauncher(context, state, actions) { pending.also { pending = null } }
    val open = rememberBackupOpenLauncher(context, actions)
    LaunchBackupPicker(state.pendingExportName, actions, create)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TaxExportCard(state, actions) { name, producer ->
            producer { bytes ->
                pending = bytes
                launchCreate(create, name, actions) { pending = null }
            }
        }
        BackupExportCard(state, actions) { launchOpen(open, actions) }
        RestoreConfirmIfNeeded(state.confirmRestore, actions)
        ToolMessages(state.status, state.error)
    }
}

@Composable
private fun rememberBackupCreateLauncher(
    context: Context,
    state: PortfolioToolsUiState,
    actions: PortfolioToolsActions,
    takePending: () -> ByteArray?,
): ActivityResultLauncher<String> = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
    actions.onPickerBusy(false)
    val bytes = takePending() ?: state.pendingExport
    val written = writePicked(context, uri, bytes)
    if (state.pendingExportName != null) actions.onExportConsumed(written)
}

@Composable
private fun rememberBackupOpenLauncher(context: Context, actions: PortfolioToolsActions): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        actions.onPickerBusy(false)
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input -> actions.onRestoreBackup(input.readBytes()) }
    }

private fun launchCreate(create: ActivityResultLauncher<String>, name: String, actions: PortfolioToolsActions, onFail: () -> Unit) {
    actions.onPickerBusy(true)
    runCatching { create.launch(name) }.onFailure { error ->
        actions.onPickerBusy(false)
        onFail()
        actions.onExportLaunchFailed(error.message ?: "Could not open the file picker.")
    }
}

private fun launchOpen(open: ActivityResultLauncher<Array<String>>, actions: PortfolioToolsActions) {
    actions.onPickerBusy(true)
    runCatching { open.launch(arrayOf("*/*")) }.onFailure { error ->
        actions.onPickerBusy(false)
        actions.onExportLaunchFailed(error.message ?: "Could not open the file picker.")
    }
}

private fun writePicked(context: Context, uri: Uri?, bytes: ByteArray?): Boolean =
    uri != null && bytes != null && writeUri(context, uri, bytes)

@Composable
private fun RestoreConfirmIfNeeded(confirm: Boolean, actions: PortfolioToolsActions) {
    if (confirm) RestoreConfirmDialog(onConfirm = actions.onConfirmRestore, onCancel = actions.onCancelRestore)
}

@Composable
private fun ToolMessages(status: String?, error: String?) {
    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun LaunchBackupPicker(fileName: String?, actions: PortfolioToolsActions, create: ActivityResultLauncher<String>) {
    LaunchedEffect(fileName) {
        if (fileName == null) return@LaunchedEffect
        launchCreate(create, fileName, actions) {}
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
private fun BackupExportCard(state: PortfolioToolsUiState, actions: PortfolioToolsActions, onPickRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Encrypted backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Not plaintext SQLite. Export wraps the file with this install’s recovery code. " +
                    "After unlock on a new phone, type the code from the backup — it can differ " +
                    "from this phone’s lock recovery. Restore asks before overwriting the live " +
                    "ledger, watchlist, templates, and alerts. The sample portfolio is not a backup.",
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
                onClick = { actions.onPrepareBackup("finsilo-backup-${LocalDate.now()}.fsi") },
                enabled = !state.busy && state.pendingExportName == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.busy) "Preparing…" else "Export backup") }
            Button(
                onClick = onPickRestore,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore backup") }
        }
    }
}

private fun writeUri(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
}.getOrDefault(false)

@Composable
private fun RestoreConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace live data?") },
        text = {
            Text(
                "Restore overwrites the ledger, watchlist, templates, and alerts on this device. Encrypted backups on disk are not deleted.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
