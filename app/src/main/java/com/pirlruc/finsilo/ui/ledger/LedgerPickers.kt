package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.ui.theme.label

@Composable
internal fun AssetPicker(assets: List<Asset>, selectedId: String?, onSelected: (String?) -> Unit) {
    val label = assets.firstOrNull { it.id == selectedId }?.let { "${it.symbol} · ${it.name}" } ?: "Select instrument"
    LedgerDropdown(
        fieldLabel = "Instrument",
        value = label,
        options = assets,
        optionLabel = { asset -> "${asset.symbol} · ${asset.name}" },
        onSelected = { asset -> onSelected(asset.id) },
    )
}

@Composable
internal fun TypePicker(selected: AssetType, onSelected: (AssetType) -> Unit) {
    val options = AssetType.entries.filter { it != AssetType.CASH }
    LedgerDropdown(
        fieldLabel = "Investment type",
        value = selected.label(),
        options = options,
        optionLabel = { it.label() },
        onSelected = onSelected,
    )
}

@Composable
internal fun CurrencyPicker(selected: Currency, onSelected: (Currency) -> Unit) {
    LedgerDropdown(
        fieldLabel = "Booking currency",
        value = selected.name,
        options = Currency.entries,
        optionLabel = { it.name },
        onSelected = onSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LedgerDropdown(fieldLabel: String, value: String, options: List<T>, optionLabel: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(fieldLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
