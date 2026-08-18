package com.pirlruc.finsilo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun PortfolioToolsCard(
    state: PortfolioToolsUiState,
    onYear: (String) -> Unit,
    onRecovery: (String) -> Unit,
    onExportCsv: ((ByteArray) -> Unit) -> Unit,
    onExportPdf: ((ByteArray) -> Unit) -> Unit,
    onExportBackup: ((ByteArray) -> Unit) -> Unit,
    onRestoreBackup: (ByteArray) -> Unit,
    onPickerBusy: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            onPickerBusy(false)
            val bytes = pending
            pending = null
            if (uri != null && bytes != null) writeUri(context, uri, bytes)
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onPickerBusy(false)
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.openInputStream(uri)?.use { input -> onRestoreBackup(input.readBytes()) }
        }

    fun saveAs(name: String, producer: ((ByteArray) -> Unit) -> Unit) {
        producer { bytes ->
            pending = bytes
            onPickerBusy(true)
            create.launch(name)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plus-valias report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Calendar-year FIFO in stored EUR. CSV and PDF share the same figures. Redemptions are labeled Resgate. This is not a full IRS pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(state.year, onYear, label = { Text("Year") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { saveAs("mais-valias-${state.year}.csv", onExportCsv) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export CSV") }
                Button(
                    onClick = { saveAs("mais-valias-${state.year}.pdf", onExportPdf) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export PDF") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Encrypted backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Not plaintext SQLite. Encrypt with the current recovery code so a new phone can restore after unlock. The sample portfolio is not a backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    state.recovery,
                    onRecovery,
                    label = { Text("Recovery code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { saveAs("finsilo-backup-${LocalDate.now()}.fsi", onExportBackup) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export backup") }
                Button(
                    onClick = {
                        onPickerBusy(true)
                        open.launch(arrayOf("*/*"))
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore backup") }
            }
        }
        state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun writeUri(context: Context, uri: Uri, bytes: ByteArray) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}
