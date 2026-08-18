package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WrapUpgradeScreen(state: LockUiState, onSaved: (Boolean) -> Unit, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Protect the ledger key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "This install still stored the database key in the Android Keystore only. Wrap it with your PIN and a new recovery code so a stolen database file is not readable without that secret. The previous recovery code will stop working.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("New recovery code", style = MaterialTheme.typography.titleMedium)
        Text(state.upgradeRecovery, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Write this down offline. It is not shown again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = state.upgradeRecoveryConfirm, onCheckedChange = onSaved)
            Text("I saved the new recovery code", modifier = Modifier.padding(top = 12.dp))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Finish upgrade") }
    }
}
