package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.ui.importcsv.BrokerImportCard
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.lock.LockUiState
import com.pirlruc.finsilo.ui.lock.LockViewModel
import com.pirlruc.finsilo.ui.lock.SecuritySettingsCard
import com.pirlruc.finsilo.ui.theme.label

@Composable
fun TargetSettingsRoute(viewModel: TargetSettingsViewModel, lock: LockViewModel, importer: BrokerImportViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lockState by lock.state.collectAsStateWithLifecycle()
    val importState by importer.state.collectAsStateWithLifecycle()
    TargetSettingsScreen(
        state = state,
        lock = lockState,
        importState = importState,
        onClose = onClose,
        onWeight = viewModel::setWeight,
        onSave = { viewModel.save(onClose) },
        onImportCsvs = { texts -> importer.importCsvs(texts) { } },
        onToggleBiometric = lock::requestToggleBiometric,
        onRotateRecovery = lock::requestRotateRecovery,
        onDismissRecovery = lock::clearNewRecovery,
        onConfirmSensitive = lock::confirmSensitiveAction,
        onCancelSensitive = lock::cancelSensitiveAction,
        onLockPin = lock::setPin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSettingsScreen(
    state: TargetSettingsUiState,
    lock: LockUiState,
    importState: BrokerImportUiState,
    onClose: () -> Unit,
    onWeight: (AssetType, String) -> Unit,
    onSave: () -> Unit,
    onImportCsvs: (List<String>) -> Unit,
    onToggleBiometric: () -> Unit,
    onRotateRecovery: () -> Unit,
    onDismissRecovery: () -> Unit,
    onConfirmSensitive: () -> Unit,
    onCancelSensitive: () -> Unit,
    onLockPin: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Target allocation") },
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
            BrokerImportCard(state = importState, onImportCsvs = onImportCsvs)
            Text(
                "Weights are percent of total NAV and must sum to 100. Drift beyond ±5% is highlighted on the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssetType.entries.forEach { type ->
                OutlinedTextField(
                    value = state.weights[type].orEmpty(),
                    onValueChange = { onWeight(type, it) },
                    label = { Text(type.label()) },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Sum ${state.sum} / 100", style = MaterialTheme.typography.titleMedium)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("Save targets")
            }
            SecuritySettingsCard(
                state = lock,
                onToggleBiometric = onToggleBiometric,
                onRotateRecovery = onRotateRecovery,
                onDismissRecovery = onDismissRecovery,
                onConfirmSensitive = onConfirmSensitive,
                onCancelSensitive = onCancelSensitive,
                onPin = onLockPin,
            )
        }
    }
}
