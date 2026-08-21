package com.pirlruc.finsilo.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

@Composable
internal fun LockChromeLayer(state: LockUiState, viewModel: LockViewModel, prompt: () -> Unit) {
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
        !state.unlocked && state.recovering -> RecoveringOverlay(state, viewModel)
        !state.unlocked || state.pendingBiometricSeal -> UnlockOverlay(state, viewModel, prompt)
    }
}

@Composable
internal fun RecoveringOverlay(state: LockUiState, viewModel: LockViewModel) {
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
internal fun UnlockOverlay(state: LockUiState, viewModel: LockViewModel, prompt: () -> Unit) {
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
internal fun LockScrim(content: @Composable () -> Unit) {
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

internal fun hiddenFromA11y(unlocked: Boolean): Modifier = if (unlocked) Modifier else Modifier.semantics { hideFromAccessibility() }
