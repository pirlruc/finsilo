package com.pirlruc.finsilo.ui.importcsv

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.nio.charset.Charset

@Composable
fun BrokerImportCard(state: BrokerImportUiState, onImportCsvs: (List<String>) -> Unit, onPickerBusy: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            onPickerBusy(false)
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            onImportCsvs(uris.mapNotNull { uri -> readCsv(context, uri) })
        }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Broker CSV import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Import a history CSV from Trading 212, DEGIRO, or Revolut. Files stay on this device. " +
                    "DEGIRO: pick Transactions and Account statement together. " +
                    "Revolut: Stocks account statement (CSV), not Profit & Loss.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    onPickerBusy(true)
                    launcher.launch(CSV_MIME_TYPES)
                },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.importing) "Importing…" else "Choose CSV files")
            }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "text/*",
    "application/csv",
    "application/vnd.ms-excel",
)

private fun readCsv(context: android.content.Context, uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
    val bytes = input.readBytes()
    decodeCsv(bytes)
}

internal fun decodeCsv(bytes: ByteArray): String {
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
    }
    val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    if ('\uFFFD' !in utf8) return utf8
    return String(bytes, Charset.forName("windows-1252")).removePrefix("\uFEFF")
}
