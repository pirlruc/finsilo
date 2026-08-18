package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.TransactionType

@Composable
fun LedgerEntryRoute(viewModel: LedgerEntryViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.prepare() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LedgerEntryScreen(
        state = state,
        onClose = onClose,
        onType = viewModel::setType,
        onDate = viewModel::setDate,
        onQuantity = viewModel::setQuantity,
        onPrice = viewModel::setUnitPrice,
        onFees = viewModel::setFees,
        onFx = viewModel::setEurPerUsd,
        onExistingAsset = viewModel::setExistingAsset,
        onNewInstrument = viewModel::setNewInstrument,
        onSymbol = viewModel::setSymbol,
        onName = viewModel::setName,
        onAssetType = viewModel::setAssetType,
        onCurrency = viewModel::setCurrency,
        onIsin = viewModel::setIsin,
        onQuoteSymbol = viewModel::setQuoteSymbol,
        onApplyTemplate = viewModel::applyTemplate,
        onSaveTemplate = viewModel::saveTemplate,
        onManualClose = viewModel::setManualClose,
        onSaveManualClose = viewModel::saveManualClose,
        onSave = { viewModel.save(onClose) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerEntryScreen(
    state: LedgerUiState,
    onClose: () -> Unit,
    onType: (TransactionType) -> Unit,
    onDate: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onPrice: (String) -> Unit,
    onFees: (String) -> Unit,
    onFx: (String) -> Unit,
    onExistingAsset: (String?) -> Unit,
    onNewInstrument: (Boolean) -> Unit,
    onSymbol: (String) -> Unit,
    onName: (String) -> Unit,
    onAssetType: (AssetType) -> Unit,
    onCurrency: (Currency) -> Unit,
    onIsin: (String) -> Unit,
    onQuoteSymbol: (String) -> Unit,
    onApplyTemplate: (LedgerTemplate) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onManualClose: (String) -> Unit,
    onSaveManualClose: () -> Unit,
    onSave: () -> Unit,
) {
    val cashLike = state.type == TransactionType.DEPOSIT_CASH || state.type == TransactionType.WITHDRAWAL
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ledger entry") },
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
            LedgerTypeChips(state.type, onType)
            LedgerTemplateSection(state.templates, state.type, onApplyTemplate, onSaveTemplate)
            OutlinedTextField(
                value = state.date,
                onValueChange = onDate,
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cashLike) {
                CashMovementFields(state, onQuantity)
            } else {
                LedgerInstrumentSection(
                    state,
                    onNewInstrument,
                    onExistingAsset,
                    onSymbol,
                    onName,
                    onAssetType,
                    onCurrency,
                    onIsin,
                    onQuoteSymbol,
                    onQuantity,
                    onPrice,
                    onFees,
                    onFx,
                )
            }
            ManualCloseFields(
                visible = !state.newInstrument && LedgerFormMapper.isLocallyValuedSelection(state),
                value = state.manualClose,
                onValue = onManualClose,
                onSave = onSaveManualClose,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerTypeChips(selected: TransactionType, onType: (TransactionType) -> Unit) {
    Text("Type", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onType(type) },
                label = { Text(type.label()) },
            )
        }
    }
}
