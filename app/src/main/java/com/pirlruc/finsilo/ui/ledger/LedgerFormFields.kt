package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType

@Composable
internal fun NewInstrumentFields(
    state: LedgerUiState,
    onSymbol: (String) -> Unit,
    onName: (String) -> Unit,
    onAssetType: (AssetType) -> Unit,
    onCurrency: (Currency) -> Unit,
    onIsin: (String) -> Unit,
    onQuoteSymbol: (String) -> Unit,
) {
    OutlinedTextField(state.symbol, onSymbol, label = {
        Text("Symbol / display ticker")
    }, singleLine = true, modifier = Modifier.fillMaxWidth())
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

@Composable
internal fun CashMovementFields(state: LedgerUiState, onQuantity: (String) -> Unit) {
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
}

@Composable
internal fun TradeAmountFields(state: LedgerUiState, onQuantity: (String) -> Unit, onPrice: (String) -> Unit, onFees: (String) -> Unit) {
    if (state.type == TransactionType.BUY) {
        Text(
            "Uninvested cash: ${state.cashEur ?: "—"} EUR. A matching cash deposit is recorded when this buy needs more.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.remainingQty != null) {
        Text(
            remainingHint(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedTextField(
        value = state.quantity,
        onValueChange = onQuantity,
        label = { Text(quantityLabel(state)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.unitPriceNative,
        onValueChange = onPrice,
        label = { Text(priceLabel(state)) },
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

@Composable
internal fun FxField(state: LedgerUiState, onFx: (String) -> Unit) {
    OutlinedTextField(
        value = state.eurPerUsd,
        onValueChange = onFx,
        label = { Text("EUR per 1 USD") },
        supportingText = {
            Text("USD market quotes (CoinGecko, commodities, US listings) convert as quote × this rate.")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun remainingHint(state: LedgerUiState): String {
    val local = LedgerFormMapper.isLocallyValuedSelection(state)
    return if (local) {
        "Remaining locally valued units: ${state.remainingQty}. Sell is a redemption."
    } else {
        "Remaining FIFO quantity: ${state.remainingQty}"
    }
}

internal fun quantityLabel(state: LedgerUiState): String =
    if (LedgerFormMapper.isLocallyValuedSelection(state) && state.type == TransactionType.SELL) {
        "Redemption quantity"
    } else {
        "Quantity"
    }

internal fun priceLabel(state: LedgerUiState): String = if (LedgerFormMapper.isLocallyValuedSelection(state)) {
    "Native unit price (redemption uses this × quantity)"
} else {
    "Native unit price"
}
