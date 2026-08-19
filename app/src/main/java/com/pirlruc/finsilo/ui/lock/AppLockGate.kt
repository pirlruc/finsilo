package com.pirlruc.finsilo.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppLockGate(viewModel: LockViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = rememberHostActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(activity) {
        viewModel.setBiometricAvailable(biometricAvailable(activity))
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.onAppBackgrounded()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val prompt =
        rememberBiometricPrompt(
            onSuccess = {
                viewModel.setBiometricPromptActive(false)
                viewModel.unlockWithBiometric()
            },
            onError = { message ->
                viewModel.setBiometricPromptActive(false)
                viewModel.setError(message)
            },
            onClosed = { viewModel.setBiometricPromptActive(false) },
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
        state.wrapUpgradeRequired ->
            WrapUpgradeScreen(
                state = state,
                onSaved = viewModel::setUpgradeRecoveryConfirm,
                onContinue = viewModel::completeWrapUpgrade,
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
                onBiometric = {
                    viewModel.setBiometricPromptActive(true)
                    prompt()
                },
                onForgot = { viewModel.showRecover(true) },
            )
        else -> content()
    }
}
