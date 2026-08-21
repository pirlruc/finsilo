package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction

@Composable
fun LockSetupScreen(
    state: LockUiState,
    onPin: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSaved: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    val enabled = !state.working
    LockScreenColumn {
        FinSiloBrand()
        Text("Protect this device copy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Set a PIN before the ledger opens. Optional biometrics wrap the same ledger key. " +
                "Losing both PIN and recovery code makes this copy of the ledger unreadable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PinSecretField(state.pin, onPin, "PIN (4–8 digits)", enabled)
        PinSecretField(state.pinConfirm, onConfirm, "Confirm PIN", enabled, ImeAction.Done, onContinue)
        Text("Recovery code", style = MaterialTheme.typography.titleMedium)
        Text(
            state.recoveryCode,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            "Write this down offline. It is not shown again. The PIN and this code wrap the SQLCipher key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CopyRecoveryButton(state.recoveryCode, enabled)
        CheckRow(state.recoveryConfirm, enabled, "I saved the recovery code", onSaved)
        if (state.biometricAvailable) {
            CheckRow(state.biometric, enabled, "Unlock with biometrics", onBiometric)
        }
        LockError(state.error)
        LockWorkingIndicator(state.working)
        Button(onClick = onContinue, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}
