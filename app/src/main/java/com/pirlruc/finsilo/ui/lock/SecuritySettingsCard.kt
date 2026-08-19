package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SecuritySettingsCard(
    state: LockUiState,
    onToggleBiometric: () -> Unit,
    onRotateRecovery: () -> Unit,
    onDismissRecovery: () -> Unit,
    onConfirmSensitive: () -> Unit,
    onCancelSensitive: () -> Unit,
    onPin: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "PIN unlocks the ledger key, not only the screen. Biometrics work after a PIN in this process. " +
                    "Changing biometrics or the recovery code requires the current PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.pendingSensitiveAction != null) {
                ConfirmSensitivePin(
                    state = state,
                    onPin = onPin,
                    onConfirm = onConfirmSensitive,
                    onCancel = onCancelSensitive,
                )
            } else {
                if (state.biometricAvailable) {
                    Button(onClick = onToggleBiometric, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.biometric) "Disable biometric unlock" else "Enable biometric unlock")
                    }
                }
                Button(onClick = onRotateRecovery, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    Text("Generate a new recovery code")
                }
            }
            if (state.newRecoveryCode != null) {
                Text(state.newRecoveryCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismissRecovery) { Text("I saved the new code") }
            }
            state.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ConfirmSensitivePin(state: LockUiState, onPin: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val label =
        when (state.pendingSensitiveAction) {
            SensitiveLockAction.ROTATE_RECOVERY -> "Enter PIN to generate a new recovery code"
            SensitiveLockAction.TOGGLE_BIOMETRIC -> "Enter PIN to change biometric unlock"
            null -> "Enter PIN"
        }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.pin,
        onValueChange = onPin,
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onConfirm, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("Confirm") }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}
