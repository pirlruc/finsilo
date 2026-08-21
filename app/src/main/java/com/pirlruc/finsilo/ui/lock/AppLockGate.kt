package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppLockGate(viewModel: LockViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RelockOnProcessStop(viewModel)
    BiometricAvailability(viewModel)
    val prompt = biometricLauncher(viewModel)
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
