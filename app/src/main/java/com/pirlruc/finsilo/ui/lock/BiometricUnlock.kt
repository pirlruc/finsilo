package com.pirlruc.finsilo.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val prompt = remember(activity, executor, onResult, onError, onClosed) {
        if (activity == null || executor == null) {
            null
        } else {
            BiometricPrompt(activity, executor, biometricCallback(onResult, onError, onClosed))
        }
    }
    return launchPrompt@{
        val host = prompt
        if (host == null) {
            onError("Unlock is unavailable.")
            return@launchPrompt
        }
        val crypto = runCatching { cryptoFor(mode(), wrapBlob()) }.getOrElse { error ->
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
