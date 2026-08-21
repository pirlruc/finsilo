package com.pirlruc.finsilo.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun biometricLauncher(viewModel: LockViewModel): () -> Unit {
    val raw = rememberBiometricPrompt(
        mode = { promptMode(viewModel.state.value, viewModel) },
        wrapBlob = { viewModel.biometricWrapBlob() },
        onResult = { result -> applyBiometricResult(viewModel, result) },
        onError = { message -> onBiometricError(viewModel, message) },
        onClosed = { viewModel.setBiometricPromptActive(false) },
    )
    return {
        viewModel.setBiometricPromptActive(true)
        raw()
    }
}

internal fun promptMode(state: LockUiState, viewModel: LockViewModel): BiometricCryptoMode = when {
    state.pendingBiometricSeal -> BiometricCryptoMode.SEAL
    viewModel.biometricWrapBlob() != null -> BiometricCryptoMode.UNWRAP
    else -> BiometricCryptoMode.CONFIRM
}

internal fun applyBiometricResult(viewModel: LockViewModel, result: BiometricPrompt.AuthenticationResult) {
    if (finishSeal(viewModel, result)) return
    if (finishUnwrap(viewModel, result)) return
    if (!BiometricSessionCipher.confirm(result)) {
        viewModel.setError("Biometric unlock failed")
        return
    }
    viewModel.unlockWithBiometric()
}

@Composable
internal fun AutoBiometricPrompt(state: LockUiState, prompt: () -> Unit) {
    var prompted by remember { mutableStateOf(false) }
    val shouldPrompt = shouldAutoPrompt(state)
    LaunchedEffect(shouldPrompt) {
        if (!shouldPrompt) {
            prompted = false
        } else if (!prompted) {
            prompted = true
            prompt()
        }
    }
}

internal fun shouldAutoPrompt(state: LockUiState): Boolean {
    if (!state.biometric || !state.biometricAvailable || state.working) return false
    if (state.pendingBiometricSeal) return true
    return state.setupComplete && !state.unlocked && !state.recovering && !state.pinFallback
}

private fun onBiometricError(viewModel: LockViewModel, message: String) {
    viewModel.setBiometricPromptActive(false)
    if (message == BIOMETRIC_PIN_FALLBACK) {
        viewModel.showPinFallback()
    } else {
        viewModel.setError(message)
    }
    if (viewModel.state.value.pendingBiometricSeal) viewModel.cancelBiometricSeal()
}

private fun finishSeal(viewModel: LockViewModel, result: BiometricPrompt.AuthenticationResult): Boolean {
    if (!viewModel.state.value.pendingBiometricSeal) return false
    val plain = viewModel.sessionKeyCopy()
    try {
        val blob = if (plain == null) null else BiometricKeyWrap.seal(result, plain)
        if (blob == null) {
            viewModel.cancelBiometricSeal()
            viewModel.setError("Could not enable biometric unlock.")
        } else {
            viewModel.finishBiometricSeal(blob)
        }
    } finally {
        plain?.fill(0)
    }
    return true
}

private fun finishUnwrap(viewModel: LockViewModel, result: BiometricPrompt.AuthenticationResult): Boolean {
    val wrap = viewModel.biometricWrapBlob() ?: return false
    val key = BiometricKeyWrap.open(result, wrap)
    try {
        if (key == null) {
            viewModel.showPinFallback()
            viewModel.setError("Unlock with biometrics failed. Enter PIN.")
        } else {
            viewModel.unlockWithUnwrappedKey(key)
        }
    } finally {
        key?.fill(0)
    }
    return true
}
