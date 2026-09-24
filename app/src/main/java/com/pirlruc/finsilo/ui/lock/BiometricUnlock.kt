package com.pirlruc.finsilo.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal const val BIOMETRIC_PIN_FALLBACK: String = "PIN_FALLBACK"

internal fun biometricAvailable(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
internal fun rememberHostActivity(): FragmentActivity? = LocalActivity.current as? FragmentActivity

internal enum class BiometricCryptoMode {
    SEAL,
    UNWRAP,
    CONFIRM,
}

@Composable
internal fun rememberBiometricPrompt(
    mode: () -> BiometricCryptoMode,
    wrapBlob: () -> ByteArray?,
    onResult: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (String) -> Unit,
    onClosed: () -> Unit,
): () -> Unit {
    val activity = rememberHostActivity()
    val executor = remember(activity) { activity?.let { ContextCompat.getMainExecutor(it) } }
    val onResultState by rememberUpdatedState(onResult)
    val onErrorState by rememberUpdatedState(onError)
    val onClosedState by rememberUpdatedState(onClosed)
    val modeState by rememberUpdatedState(mode)
    val wrapState by rememberUpdatedState(wrapBlob)
    var prompt by remember { mutableStateOf<BiometricPrompt?>(null) }
    // BiometricPrompt commits a fragment. Doing that inside remember() runs during
    // composition and crashes with FragmentManager "already executing transactions"
    // as soon as the PIN or biometric screen is shown.
    DisposableEffect(activity, executor) {
        val host = activity
        val exec = executor
        prompt =
            if (host == null || exec == null) {
                null
            } else {
                BiometricPrompt(
                    host,
                    exec,
                    biometricCallback(
                        onResult = { onResultState(it) },
                        onError = { onErrorState(it) },
                        onClosed = { onClosedState() },
                    ),
                )
            }
        onDispose { prompt = null }
    }
    return launchPrompt@{
        val host = prompt
        if (host == null) {
            onErrorState("Unlock is unavailable.")
            return@launchPrompt
        }
        val crypto = runCatching { cryptoFor(modeState(), wrapState()) }.getOrElse { error ->
            onErrorState(error.message ?: "Biometric unlock failed")
            return@launchPrompt
        }
        runCatching { host.authenticate(biometricPromptInfo(), crypto) }
            .onFailure { error -> onErrorState(error.message ?: "Biometric unlock failed") }
    }
}

private fun biometricPromptInfo(): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Unlock FinSilo")
    .setNegativeButtonText("Use PIN")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .build()

private fun cryptoFor(mode: BiometricCryptoMode, blob: ByteArray?): BiometricPrompt.CryptoObject = when (mode) {
    BiometricCryptoMode.SEAL -> BiometricKeyWrap.encryptObject()
    BiometricCryptoMode.UNWRAP -> BiometricKeyWrap.decryptObject(blob ?: error("Biometric wrap is missing."))
    BiometricCryptoMode.CONFIRM -> BiometricSessionCipher.cryptoObject()
}

private fun biometricCallback(
    onResult: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (String) -> Unit,
    onClosed: () -> Unit,
): BiometricPrompt.AuthenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        onClosed()
        onResult(result)
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        onClosed()
        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
            errorCode != BiometricPrompt.ERROR_USER_CANCELED
        ) {
            onError(errString.toString())
        } else {
            onError(BIOMETRIC_PIN_FALLBACK)
        }
    }
}
