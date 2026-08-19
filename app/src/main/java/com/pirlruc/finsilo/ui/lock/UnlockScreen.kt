package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pirlruc.finsilo.domain.lock.AppLockCrypto

@Composable
fun UnlockScreen(state: LockUiState, onPin: (String) -> Unit, onUnlock: () -> Unit, onBiometric: () -> Unit, onForgot: () -> Unit) {
    val enabled = !state.working
    val canUnlock = enabled && AppLockCrypto.pinOk(state.pin)
    LockScreenColumn {
        FinSiloBrand()
        Text("Unlock the silo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Enter your PIN. Biometrics work only after a PIN unlock in this process; after the app is killed you must enter the PIN again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PinSecretField(state.pin, onPin, "PIN", enabled, ImeAction.Done, onDone = { if (canUnlock) onUnlock() })
        LockError(state.error)
        LockWorkingIndicator(state.working)
        Button(onClick = onUnlock, enabled = canUnlock, modifier = Modifier.fillMaxWidth()) { Text("Unlock") }
        if (state.biometric && state.biometricAvailable) {
            Button(onClick = onBiometric, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Use biometrics") }
        }
        TextButton(onClick = onForgot, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Forgot PIN") }
    }
}

@Composable
fun RecoverPinScreen(
    state: LockUiState,
    onRecovery: (String) -> Unit,
    onNewPin: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val enabled = !state.working
    LockScreenColumn {
        FinSiloBrand()
        Text("Recover access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Enter the recovery code shown when you first set the PIN, then choose a new PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.pin,
            onValueChange = onRecovery,
            label = { Text("Recovery code") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        PinSecretField(state.pinConfirm, onNewPin, "New PIN (4–8 digits)", enabled, ImeAction.Done, onSubmit)
        LockError(state.error)
        LockWorkingIndicator(state.working)
        Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Reset PIN") }
        TextButton(onClick = onBack, enabled = enabled) { Text("Back") }
    }
}
