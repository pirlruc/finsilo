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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.ui.settings.BoundedBytes
import java.nio.charset.Charset

@Composable
fun BrokerImportCard(
    state: BrokerImportUiState,
    onImportCsvs: (List<String>) -> Unit,
    onPickerBusy: (Boolean) -> Unit = {},
    onQuoteSymbol: (String, String) -> Unit = { _, _ -> },
    onConfirmReview: () -> Unit = {},
    onCancelReview: () -> Unit = {},
    onPickerError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            onPickerBusy(false)
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val picked = uris.mapNotNull { uri -> readCsv(context, uri) }
            val unsupported = picked.filterIsInstance<PickedCsv.Unsupported>().firstOrNull()
            if (unsupported != null) {
                onPickerError(unsupported.message)
                return@rememberLauncherForActivityResult
            }
            onImportCsvs(picked.filterIsInstance<PickedCsv.Text>().map { it.body })
        }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Broker CSV import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.reviewing) {
                ImportReviewList(state.drafts, state.importing, onQuoteSymbol, onConfirmReview, onCancelReview)
            } else {
                ImportPicker(
                    importing = state.importing,
                    onPick = {
                        onPickerBusy(true)
                        launcher.launch(CSV_MIME_TYPES)
                    },
                )
            }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ImportPicker(importing: Boolean, onPick: () -> Unit) {
    Text(
        "Import a history CSV from Trading 212, DEGIRO, or Revolut. You can import again later; duplicate rows are skipped. " +
            "DEGIRO: pick Transactions and Account statement together (CSV, any language). " +
            "Revolut: Stocks/Invest account statement CSV, not Profit & Loss and not Excel. " +
            "Lots always import; missing quotes are a warning you can fix before saving.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onPick, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
        Text(if (importing) "Importing…" else "Choose CSV files")
    }
}

@Composable
private fun ImportReviewList(
    drafts: List<ImportSymbolDraft>,
    importing: Boolean,
    onQuoteSymbol: (String, String) -> Unit,
    onConfirmReview: () -> Unit,
    onCancelReview: () -> Unit,
) {
    Text(
        "Edit quote symbols for names the feed did not recognise. Historical lots are kept either way.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    drafts.forEach { draft ->
        OutlinedTextField(
            value = draft.quoteSymbol,
            onValueChange = { onQuoteSymbol(draft.key, it) },
            label = { Text("${draft.symbol} quote symbol") },
            supportingText = {
                val warning = draft.quoteWarning
                Text(warning ?: draft.name)
            },
            isError = draft.quoteWarning != null,
            singleLine = true,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = onConfirmReview, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
        Text(if (importing) "Importing…" else "Import lots")
    }
    TextButton(onClick = onCancelReview, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

private val CSV_MIME_TYPES = arrayOf(
    "*/*",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "text/*",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

private sealed interface PickedCsv {
    data class Text(val body: String) : PickedCsv
    data class Unsupported(val message: String) : PickedCsv
}

private fun readCsv(context: android.content.Context, uri: Uri): PickedCsv? = context.contentResolver.openInputStream(uri)?.use { input ->
    val bytes = BoundedBytes.read(input)
    pickerError(bytes)?.let { return@use PickedCsv.Unsupported(it) }
    PickedCsv.Text(decodeCsv(bytes))
}

internal fun pickerError(bytes: ByteArray): String? {
    if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
        return "Excel (.xlsx) is not supported. Export CSV from the broker."
    }
    if (bytes.size >= 4 &&
        bytes[0] == 0xD0.toByte() &&
        bytes[1] == 0xCF.toByte() &&
        bytes[2] == 0x11.toByte() &&
        bytes[3] == 0xE0.toByte()
    ) {
        return "Excel (.xls) is not supported. Export CSV from the broker."
    }
    return null
}

internal fun decodeCsv(bytes: ByteArray): String {
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
    }
    val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    if ('\uFFFD' !in utf8) return utf8
    return String(bytes, Charset.forName("windows-1252")).removePrefix("\uFEFF")
}
