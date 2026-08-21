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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.ui.importcsv.BrokerImportCard
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.lock.LockUiState
import com.pirlruc.finsilo.ui.lock.LockViewModel
import com.pirlruc.finsilo.ui.lock.SecuritySettingsCard
import com.pirlruc.finsilo.ui.theme.label

@Composable
fun TargetSettingsRoute(
    targets: TargetSettingsViewModel,
    lock: LockViewModel,
    importer: BrokerImportViewModel,
    onClose: () -> Unit,
    onPickerBusy: (Boolean) -> Unit,
    container: AppContainer,
) {
    val state by targets.state.collectAsStateWithLifecycle()
    val lockState by lock.state.collectAsStateWithLifecycle()
    val importState by importer.state.collectAsStateWithLifecycle()
    val tools: PortfolioToolsViewModel = viewModel(factory = PortfolioToolsViewModel.factory(container))
    val toolsState by tools.state.collectAsStateWithLifecycle()
    TargetSettingsScreen(
        state = state,
        lock = lockState,
        importState = importState,
        tools = toolsState,
        actions = TargetSettingsActions(
            onClose = onClose,
            onWeight = targets::setWeight,
            onSave = { targets.save(onClose) },
            onImportCsvs = { texts -> importer.importCsvs(texts) { } },
            onDraftQuote = importer::setDraftQuote,
            onConfirmImport = { importer.confirmImport { } },
            onCancelImport = importer::cancelReview,
            onPickerBusy = onPickerBusy,
            lock = LockSettingsActions(
                onToggleBiometric = lock::requestToggleBiometric,
                onRotateRecovery = lock::requestRotateRecovery,
                onDismissRecovery = lock::clearNewRecovery,
                onConfirmSensitive = lock::confirmSensitiveAction,
                onCancelSensitive = lock::cancelSensitiveAction,
                onLockPin = lock::setPin,
            ),
            tools = PortfolioToolsActions(
                onYear = tools::setYear,
                onRecovery = tools::setRecovery,
                onExportCsv = tools::exportTaxCsv,
                onExportPdf = tools::exportTaxPdf,
                onPrepareBackup = tools::prepareBackupExport,
                onExportConsumed = tools::onExportConsumed,
                onExportLaunchFailed = tools::onExportLaunchFailed,
                onRestoreBackup = tools::restoreBackup,
                onConfirmRestore = tools::confirmRestore,
                onCancelRestore = tools::cancelRestore,
                onPickerBusy = onPickerBusy,
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetSettingsScreen(
    state: TargetSettingsUiState,
    lock: LockUiState,
    importState: BrokerImportUiState,
    tools: PortfolioToolsUiState,
    actions: TargetSettingsActions,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Data", style = MaterialTheme.typography.titleLarge)
            BrokerImportCard(
                state = importState,
                onImportCsvs = actions.onImportCsvs,
                onQuoteSymbol = actions.onDraftQuote,
                onConfirmReview = actions.onConfirmImport,
                onCancelReview = actions.onCancelImport,
                onPickerBusy = actions.onPickerBusy,
            )
            PortfolioToolsCard(state = tools, actions = actions.tools)
            Text("Target allocation", style = MaterialTheme.typography.titleLarge)
            Text(
                "Weights are percent of total NAV and must sum to 100. Drift beyond ±5% is highlighted on the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssetType.entries.forEach { type ->
                OutlinedTextField(
                    value = state.weights[type].orEmpty(),
                    onValueChange = { actions.onWeight(type, it) },
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
            Button(onClick = actions.onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("Save targets")
            }
            Text("Security", style = MaterialTheme.typography.titleLarge)
            SecuritySettingsCard(
                state = lock,
                onToggleBiometric = actions.lock.onToggleBiometric,
                onRotateRecovery = actions.lock.onRotateRecovery,
                onDismissRecovery = actions.lock.onDismissRecovery,
                onConfirmSensitive = actions.lock.onConfirmSensitive,
                onCancelSensitive = actions.lock.onCancelSensitive,
                onPin = actions.lock.onLockPin,
            )
        }
    }
}
