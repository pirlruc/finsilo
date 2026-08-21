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
fun UnlockScreen(
    state: LockUiState,
    onPin: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
    onPinFallback: () -> Unit,
) {
    if (state.pendingBiometricSeal) {
        SealBiometricsPane(state, onBiometric, onPinFallback)
    } else {
        UnlockForm(state, onPin, onUnlock, onBiometric, onForgot, onPinFallback)
    }
}

@Composable
private fun UnlockForm(
    state: LockUiState,
    onPin: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
    onPinFallback: () -> Unit,
) {
    val enabled = !state.working
    val canUnlock = enabled && AppLockCrypto.pinOk(state.pin)
    val showPin = !state.biometric || !state.biometricAvailable || state.pinFallback
    LockScreenColumn {
        FinSiloBrand()
        Text("Unlock the silo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        UnlockHint(showPin)
        if (showPin) {
            PinSecretField(state.pin, onPin, "PIN", enabled, ImeAction.Done, onDone = { if (canUnlock) onUnlock() })
        }
        LockError(state.error)
        LockWorkingIndicator(state.working)
        if (showPin) {
            Button(onClick = onUnlock, enabled = canUnlock, modifier = Modifier.fillMaxWidth()) { Text("Unlock") }
        }
        if (state.biometric && state.biometricAvailable && !state.pinFallback) {
            Button(onClick = onBiometric, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Use biometrics") }
            TextButton(onClick = onPinFallback, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Use PIN") }
        }
        TextButton(onClick = onForgot, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Forgot PIN") }
    }
}

@Composable
private fun UnlockHint(showPin: Boolean) {
    val text =
        if (showPin) {
            "Enter your PIN. Recovery still unwraps the ledger if the PIN is lost."
        } else {
            "Unlock with biometrics, or use PIN. After a biometric enrollment change you will be asked for the PIN."
        }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SealBiometricsPane(state: LockUiState, onBiometric: () -> Unit, onSkip: () -> Unit) {
    val enabled = !state.working
    LockScreenColumn {
        FinSiloBrand()
        Text("Enable biometric unlock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Confirm your fingerprint or face to wrap the ledger key. PIN and recovery stay available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LockError(state.error)
        LockWorkingIndicator(state.working)
        Button(onClick = onBiometric, enabled = enabled && state.biometricAvailable, modifier = Modifier.fillMaxWidth()) {
            Text("Use biometrics")
        }
        TextButton(onClick = onSkip, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
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
