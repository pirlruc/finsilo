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
import com.pirlruc.finsilo.domain.model.TransactionType

@Composable
fun LedgerEntryRoute(viewModel: LedgerEntryViewModel, onClose: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.prepare() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = LedgerFormActions(
        nav = LedgerNavActions(
            onClose = onClose,
            onSave = { viewModel.save(onClose) },
            onApplyTemplate = viewModel::applyTemplate,
            onSaveTemplate = viewModel::saveTemplate,
            onManualClose = viewModel::setManualClose,
            onSaveManualClose = viewModel::saveManualClose,
        ),
        amounts = LedgerAmountActions(
            onType = viewModel::setType,
            onDate = viewModel::setDate,
            onQuantity = viewModel::setQuantity,
            onPrice = viewModel::setUnitPrice,
            onFees = viewModel::setFees,
            onFx = viewModel::setEurPerUsd,
        ),
        instrument = LedgerInstrumentActions(
            onNewInstrument = viewModel::setNewInstrument,
            onExistingAsset = viewModel::setExistingAsset,
            onSymbol = viewModel::setSymbol,
            onName = viewModel::setName,
            onAssetType = viewModel::setAssetType,
            onCurrency = viewModel::setCurrency,
            onIsin = viewModel::setIsin,
            onQuoteSymbol = viewModel::setQuoteSymbol,
        ),
    )
    LedgerEntryScreen(state = state, actions = actions)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LedgerEntryScreen(state: LedgerUiState, actions: LedgerFormActions) {
    val cashLike = state.type == TransactionType.DEPOSIT_CASH || state.type == TransactionType.WITHDRAWAL
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ledger entry") },
                navigationIcon = {
                    IconButton(onClick = actions.nav.onClose) {
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
            LedgerTypeChips(state.type, actions.amounts.onType)
            LedgerTemplateSection(state.templates, state.type, actions.nav.onApplyTemplate, actions.nav.onSaveTemplate)
            OutlinedTextField(
                value = state.date,
                onValueChange = actions.amounts.onDate,
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cashLike) {
                CashMovementFields(state, actions.amounts.onQuantity)
            } else {
                LedgerInstrumentSection(state, actions.instrument, actions.amounts)
            }
            ManualCloseFields(
                visible = !state.newInstrument && LedgerFormMapper.isLocallyValuedSelection(state),
                value = state.manualClose,
                onValue = actions.nav.onManualClose,
                onSave = actions.nav.onSaveManualClose,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = actions.nav.onSave,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
