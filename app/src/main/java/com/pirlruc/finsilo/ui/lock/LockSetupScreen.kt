package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LockSetupScreen(
    state: LockUiState,
    onPin: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSaved: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Protect this device copy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Set a PIN before the ledger opens. Optionally unlock later with biometrics. Save the recovery code; it is the only way to reset a forgotten PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.pin,
            onValueChange = onPin,
            label = { Text("PIN (4–8 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.pinConfirm,
            onValueChange = onConfirm,
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Recovery code", style = MaterialTheme.typography.titleMedium)
        Text(state.recoveryCode, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Write this down offline. It is not shown again. Losing both PIN and recovery code locks the UI; the SQLCipher database still needs this device's keystore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = state.recoveryConfirm, onCheckedChange = onSaved)
            Text("I saved the recovery code", modifier = Modifier.padding(top = 12.dp))
        }
        if (state.biometricAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = state.biometric, onCheckedChange = onBiometric)
                Text("Unlock with biometrics", modifier = Modifier.padding(top = 12.dp))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}
