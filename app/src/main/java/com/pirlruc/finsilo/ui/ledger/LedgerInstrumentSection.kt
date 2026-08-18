package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.TransactionType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerInstrumentSection(state: LedgerUiState, instrument: LedgerInstrumentActions, amounts: LedgerAmountActions) {
    val interest = state.type == TransactionType.INTEREST
    if (state.type == TransactionType.BUY) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.newInstrument,
                onClick = { instrument.onNewInstrument(true) },
                label = { Text("New instrument") },
            )
            FilterChip(
                selected = !state.newInstrument,
                onClick = { instrument.onNewInstrument(false) },
                label = { Text("Existing") },
            )
        }
    }
    if (state.type != TransactionType.BUY || !state.newInstrument) {
        AssetPicker(
            assets = LedgerFormMapper.selectableAssets(state),
            selectedId = state.existingAssetId,
            onSelected = instrument.onExistingAsset,
        )
    }
    if (state.type == TransactionType.BUY && state.newInstrument) {
        NewInstrumentFields(
            state,
            instrument.onSymbol,
            instrument.onName,
            instrument.onAssetType,
            instrument.onCurrency,
            instrument.onIsin,
            instrument.onQuoteSymbol,
        )
    }
    if (interest) {
        OutlinedTextField(
            value = state.unitPriceNative,
            onValueChange = amounts.onPrice,
            label = { Text("Interest amount (native)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TradeAmountFields(state, amounts.onQuantity, amounts.onPrice, amounts.onFees)
    }
    if (LedgerFormMapper.needsFxField(state)) {
        FxField(state, amounts.onFx)
    }
}
