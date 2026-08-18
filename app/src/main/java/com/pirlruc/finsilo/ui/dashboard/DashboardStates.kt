package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.ui.importcsv.BrokerImportCard
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState

@Composable
internal fun EmptyState(
    onLoadSample: () -> Unit,
    onAddTransaction: () -> Unit,
    importState: BrokerImportUiState,
    onImportCsvs: (List<String>) -> Unit,
    onPickerBusy: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No holdings yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This dashboard reads the on-device encrypted ledger. Add a buy or sell, import a broker CSV, or load a synthetic sample.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddTransaction) { Text("Add transaction") }
        Spacer(Modifier.height(12.dp))
        BrokerImportCard(state = importState, onImportCsvs = onImportCsvs, onPickerBusy = onPickerBusy)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLoadSample) { Text("Load sample portfolio") }
    }
}

@Composable
internal fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Could not load dashboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}
