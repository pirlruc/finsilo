package com.pirlruc.finsilo.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal fun biometricAvailable(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
internal fun rememberHostActivity(): FragmentActivity? = LocalActivity.current as? FragmentActivity

@Composable
internal fun rememberBiometricPrompt(onSuccess: () -> Unit, onError: (String) -> Unit, onClosed: () -> Unit): () -> Unit {
    val activity = rememberHostActivity()
    val executor = remember(activity) { activity?.let { ContextCompat.getMainExecutor(it) } }
    val prompt = remember(activity, executor, onSuccess, onError, onClosed) {
        if (activity == null || executor == null) {
            null
        } else {
            BiometricPrompt(activity, executor, biometricCallback(onSuccess, onError, onClosed))
        }
    }
    return launchPrompt@{
        val host = prompt
        if (host == null) {
            onError("Unlock is unavailable.")
            return@launchPrompt
        }
        val crypto = runCatching { BiometricSessionCipher.cryptoObject() }.getOrElse { error ->
            onError(error.message ?: "Biometric unlock failed")
            return@launchPrompt
        }
        host.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock FinSilo")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
            crypto,
        )
    }
}

private fun biometricCallback(
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onClosed: () -> Unit,
): BiometricPrompt.AuthenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        onClosed()
        if (!BiometricSessionCipher.confirm(result)) {
            onError("Biometric unlock failed")
            return
        }
        onSuccess()
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        onClosed()
        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
            errorCode != BiometricPrompt.ERROR_USER_CANCELED
        ) {
            onError(errString.toString())
        }
    }
}
