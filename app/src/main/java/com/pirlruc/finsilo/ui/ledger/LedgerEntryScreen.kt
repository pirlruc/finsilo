package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.ui.theme.label

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
    onSave: () -> Unit,
) {
    val cashLike = state.type == TransactionType.DEPOSIT_CASH || state.type == TransactionType.WITHDRAWAL
    val interest = state.type == TransactionType.INTEREST
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
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onType(type) },
                        label = { Text(type.label()) },
                    )
                }
            }
            OutlinedTextField(
                value = state.date,
                onValueChange = onDate,
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cashLike) {
                Text(
                    "Uninvested cash: ${state.cashEur ?: "—"} EUR. Withdrawals above this amount are refused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = onQuantity,
                    label = { Text(if (state.type == TransactionType.WITHDRAWAL) "Withdrawal EUR" else "Deposit EUR") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (state.type == TransactionType.BUY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.newInstrument, onClick = { onNewInstrument(true) }, label = { Text("New instrument") })
                        FilterChip(selected = !state.newInstrument, onClick = { onNewInstrument(false) }, label = { Text("Existing") })
                    }
                }
                if (state.type != TransactionType.BUY || !state.newInstrument) {
                    AssetPicker(
                        assets = selectableAssets(state),
                        selectedId = state.existingAssetId,
                        onSelected = onExistingAsset,
                    )
                }
                if (state.type == TransactionType.BUY && state.newInstrument) {
                    NewInstrumentFields(state, onSymbol, onName, onAssetType, onCurrency, onIsin, onQuoteSymbol)
                }
                if (interest) {
                    OutlinedTextField(
                        value = state.unitPriceNative,
                        onValueChange = onPrice,
                        label = { Text("Interest amount (native)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    if (state.remainingQty != null) {
                        Text(
                            "Remaining FIFO quantity: ${state.remainingQty}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = onQuantity,
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.unitPriceNative,
                        onValueChange = onPrice,
                        label = { Text("Native unit price") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.feesEur,
                        onValueChange = onFees,
                        label = { Text("Fees EUR") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val usd = selectedCurrency(state) == Currency.USD
                if (usd) {
                    OutlinedTextField(
                        value = state.eurPerUsd,
                        onValueChange = onFx,
                        label = { Text("EUR per 1 USD") },
                        supportingText = { Text("USD amounts convert as native × this rate.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun NewInstrumentFields(
    state: LedgerUiState,
    onSymbol: (String) -> Unit,
    onName: (String) -> Unit,
    onAssetType: (AssetType) -> Unit,
    onCurrency: (Currency) -> Unit,
    onIsin: (String) -> Unit,
    onQuoteSymbol: (String) -> Unit,
) {
    OutlinedTextField(state.symbol, onSymbol, label = { Text("Symbol / display ticker") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(state.name, onName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TypePicker(state.assetType, onAssetType)
    CurrencyPicker(state.currency, onCurrency)
    OutlinedTextField(
        state.isin,
        onIsin,
        label = { Text("ISIN (optional)") },
        supportingText = { Text("Store PPR / fund identifiers separately from the display name.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state.quoteSymbol,
        onQuoteSymbol,
        label = { Text("Quote symbol (optional)") },
        supportingText = { Text("Listed ticker for Stooq / Alpha Vantage. Leave blank for unlisted PPR NAV.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPicker(assets: List<Asset>, selectedId: String?, onSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = assets.firstOrNull { it.id == selectedId }?.let { "${it.symbol} · ${it.name}" } ?: "Select instrument"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Instrument") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            assets.forEach { asset ->
                DropdownMenuItem(
                    text = { Text("${asset.symbol} · ${asset.name}") },
                    onClick = {
                        onSelected(asset.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(selected: AssetType, onSelected: (AssetType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = AssetType.entries.filter { it != AssetType.CASH }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Investment type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { type ->
                DropdownMenuItem(text = { Text(type.label()) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(selected: Currency, onSelected: (Currency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Native currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currency.entries.forEach { currency ->
                DropdownMenuItem(text = { Text(currency.name) }, onClick = { onSelected(currency); expanded = false })
            }
        }
    }
}

private fun selectableAssets(state: LedgerUiState): List<Asset> =
    when (state.type) {
        TransactionType.INTEREST -> state.assets.filter { it.assetType.allowsInterest }
        TransactionType.DIVIDEND -> state.assets.filter { it.assetType.allowsDividend }
        TransactionType.SELL, TransactionType.BUY -> state.assets
        else -> emptyList()
    }

private fun selectedCurrency(state: LedgerUiState): Currency {
    if (state.type == TransactionType.BUY && state.newInstrument) return state.currency
    return state.assets.firstOrNull { it.id == state.existingAssetId }?.baseCurrency ?: Currency.EUR
}

private fun TransactionType.label(): String =
    when (this) {
        TransactionType.BUY -> "Buy"
        TransactionType.SELL -> "Sell"
        TransactionType.DEPOSIT_CASH -> "Deposit"
        TransactionType.WITHDRAWAL -> "Withdrawal"
        TransactionType.DIVIDEND -> "Dividend"
        TransactionType.INTEREST -> "Interest"
    }
