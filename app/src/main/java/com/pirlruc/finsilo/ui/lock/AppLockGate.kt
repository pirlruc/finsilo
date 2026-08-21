package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppLockGate(viewModel: LockViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RelockOnProcessStop(viewModel)
    BiometricAvailability(viewModel)
    val prompt = biometricLauncher(viewModel, state)
    AutoBiometricPrompt(state, prompt)
    var sessionReady by remember { mutableStateOf(false) }
    SideEffect {
        if (state.setupComplete && state.unlocked && !state.wrapUpgradeRequired) {
            sessionReady = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (sessionReady) {
            Box(Modifier.fillMaxSize().then(hiddenFromA11y(state.unlocked))) {
                content()
            }
        }
        LockChromeLayer(state, viewModel, prompt)
    }
}

@Composable
private fun biometricLauncher(viewModel: LockViewModel, state: LockUiState): () -> Unit = rememberBiometricPrompt(
    mode = { promptMode(state, viewModel) },
    wrapBlob = { viewModel.biometricWrapBlob() },
    onResult = { result -> applyBiometricResult(viewModel, result) },
    onError = { message ->
        viewModel.setBiometricPromptActive(false)
        if (message == BIOMETRIC_PIN_FALLBACK) {
            viewModel.showPinFallback()
            if (state.pendingBiometricSeal) viewModel.cancelBiometricSeal()
        } else {
            viewModel.setError(message)
            if (state.pendingBiometricSeal) viewModel.cancelBiometricSeal()
        }
    },
    onClosed = { viewModel.setBiometricPromptActive(false) },
)

private fun promptMode(state: LockUiState, viewModel: LockViewModel): BiometricCryptoMode = when {
    state.pendingBiometricSeal -> BiometricCryptoMode.SEAL
    viewModel.biometricWrapBlob() != null -> BiometricCryptoMode.UNWRAP
    else -> BiometricCryptoMode.CONFIRM
}

private fun applyBiometricResult(viewModel: LockViewModel, result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
    val state = viewModel.state.value
    if (state.pendingBiometricSeal) {
        val plain = viewModel.sessionKeyCopy()
        val blob = if (plain == null) null else BiometricKeyWrap.seal(result, plain)
        if (blob == null) {
            viewModel.cancelBiometricSeal()
            viewModel.setError("Could not enable biometric unlock.")
        } else {
            viewModel.finishBiometricSeal(blob)
        }
        return
    }
    val wrap = viewModel.biometricWrapBlob()
    if (wrap != null) {
        val key = BiometricKeyWrap.open(result, wrap)
        if (key == null) {
            viewModel.showPinFallback()
            viewModel.setError("Unlock with biometrics failed. Enter PIN.")
        } else {
            viewModel.unlockWithUnwrappedKey(key)
        }
        return
    }
    if (!BiometricSessionCipher.confirm(result)) {
        viewModel.setError("Biometric unlock failed")
        return
    }
    viewModel.unlockWithBiometric()
}

@Composable
private fun AutoBiometricPrompt(state: LockUiState, prompt: () -> Unit) {
    var prompted by remember { mutableStateOf(false) }
    val shouldPrompt =
        state.biometric &&
            state.biometricAvailable &&
            !state.working &&
            (state.pendingBiometricSeal || (state.setupComplete && !state.unlocked && !state.recovering && !state.pinFallback))
    LaunchedEffect(shouldPrompt) {
        if (!shouldPrompt) {
            prompted = false
        } else if (!prompted) {
            prompted = true
            prompt()
        }
    }
}

@Composable
private fun RelockOnProcessStop(viewModel: LockViewModel) {
    val owner = remember { ProcessLifecycleOwner.get() }
    DisposableEffect(owner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.onAppBackgrounded()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun BiometricAvailability(viewModel: LockViewModel) {
    val activity = rememberHostActivity()
    LaunchedEffect(activity) {
        if (activity != null) viewModel.setBiometricAvailable(biometricAvailable(activity))
    }
}

@Composable
private fun LockChromeLayer(state: LockUiState, viewModel: LockViewModel, prompt: () -> Unit) {
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
            RecoveringOverlay(state, viewModel)
        !state.unlocked || state.pendingBiometricSeal ->
            UnlockOverlay(state, viewModel, prompt)
    }
}

@Composable
private fun RecoveringOverlay(state: LockUiState, viewModel: LockViewModel) {
    LockScrim {
        RecoverPinScreen(
            state = state,
            onRecovery = viewModel::setRecoveryTyped,
            onNewPin = viewModel::setPinConfirm,
            onSubmit = viewModel::recoverAndResetPin,
            onBack = { viewModel.showRecover(false) },
        )
    }
}

@Composable
private fun UnlockOverlay(state: LockUiState, viewModel: LockViewModel, prompt: () -> Unit) {
    LockScrim {
        UnlockScreen(
            state = state,
            onPin = viewModel::setPin,
            onUnlock = viewModel::unlockWithPin,
            onBiometric = {
                viewModel.setBiometricPromptActive(true)
                prompt()
            },
            onForgot = { viewModel.showRecover(true) },
            onPinFallback = {
                if (state.pendingBiometricSeal) viewModel.cancelBiometricSeal() else viewModel.showPinFallback()
            },
        )
    }
}

@Composable
private fun LockScrim(content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = interaction, indication = null, onClick = {}),
    ) {
        content()
    }
}

private fun hiddenFromA11y(unlocked: Boolean): Modifier = if (unlocked) Modifier else Modifier.semantics { hideFromAccessibility() }
