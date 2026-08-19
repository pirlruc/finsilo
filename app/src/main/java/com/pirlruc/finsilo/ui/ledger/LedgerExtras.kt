package com.pirlruc.finsilo.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.TransactionType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerTemplateSection(
    templates: List<LedgerTemplate>,
    type: TransactionType,
    onApply: (LedgerTemplate) -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    Text("Templates", style = MaterialTheme.typography.labelLarge)
    Text(
        "Buy, interest, and cash deposit only. Applying a template fills the form; it does not post until you save.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (templates.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.forEach { template ->
                FilterChip(selected = false, onClick = { onApply(template) }, label = { Text(template.label) })
            }
        }
    }
    if (type == TransactionType.BUY || type == TransactionType.INTEREST || type == TransactionType.DEPOSIT_CASH) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("New template name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(label) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save as template")
        }
    }
}

@Composable
internal fun ManualCloseFields(visible: Boolean, value: String, onValue: (String) -> Unit, onSave: () -> Unit) {
    if (!visible) return
    Text(
        "Statement close is stored as a daily bar for this unlisted instrument. Listed tickers stay on GET-only feeds. Value = remaining units × this native price.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Manual close (native, per unit)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        Text("Save statement close")
    }
}
