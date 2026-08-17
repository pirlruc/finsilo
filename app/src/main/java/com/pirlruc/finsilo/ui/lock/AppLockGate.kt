package com.pirlruc.finsilo.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppLockGate(viewModel: LockViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = rememberHostActivity()
    LaunchedEffect(activity) {
        viewModel.setBiometricAvailable(biometricAvailable(activity))
    }
    val prompt = rememberBiometricPrompt(
        onSuccess = viewModel::unlockWithBiometric,
        onError = viewModel::setError,
    )
    when {
        !state.setupComplete ->
            LockSetupScreen(
                state = state,
                onPin = viewModel::setPin,
                onConfirm = viewModel::setPinConfirm,
                onSaved = viewModel::setRecoveryConfirm,
                onBiometric = viewModel::setBiometric,
                onContinue = viewModel::completeSetup,
            )
        !state.unlocked && state.recovering ->
            RecoverPinScreen(
                state = state,
                onRecovery = viewModel::setRecoveryTyped,
                onNewPin = viewModel::setPinConfirm,
                onSubmit = viewModel::recoverAndResetPin,
                onBack = { viewModel.showRecover(false) },
            )
        !state.unlocked ->
            UnlockScreen(
                state = state,
                onPin = viewModel::setPin,
                onUnlock = viewModel::unlockWithPin,
                onBiometric = prompt,
                onForgot = { viewModel.showRecover(true) },
            )
        else -> content()
    }
}
