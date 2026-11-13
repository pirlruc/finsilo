package com.pirlruc.finsilo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.vault.VaultRecord

/** Switches the unlocked ledger. Each vault keeps its own database file and PIN. */
@Composable
internal fun VaultSwitcherCard(vaults: List<VaultRecord>, activeId: String, onSwitch: (String) -> Unit, onCreate: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vaults", style = MaterialTheme.typography.titleMedium)
            Text(
                "Each vault is its own database and PIN. The original ledger stays in finsilo.db.",
                style = MaterialTheme.typography.bodySmall,
            )
            vaults.forEach { vault ->
                val current = if (vault.id == activeId) " (open)" else ""
                Button(onClick = { onSwitch(vault.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(vault.label + current)
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("New vault") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                onCreate(label)
                label = ""
            }) { Text("Add vault") }
        }
    }
}
