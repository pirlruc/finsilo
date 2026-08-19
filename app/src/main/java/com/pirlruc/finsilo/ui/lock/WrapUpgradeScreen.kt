package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun WrapUpgradeScreen(state: LockUiState, onSaved: (Boolean) -> Unit, onContinue: () -> Unit) {
    val enabled = !state.working
    LockScreenColumn {
        FinSiloBrand()
        Text("Protect the ledger key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "This install still stored the database key in the Android Keystore only. Wrap it with your PIN and a new recovery code so a stolen database file is not readable without that secret. The previous recovery code will stop working.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("New recovery code", style = MaterialTheme.typography.titleMedium)
        Text(
            state.upgradeRecovery,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            "Write this down offline. It is not shown again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CheckRow(state.upgradeRecoveryConfirm, enabled, "I saved the new recovery code", onSaved)
        LockError(state.error)
        LockWorkingIndicator(state.working)
        Button(onClick = onContinue, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Finish upgrade") }
    }
}
