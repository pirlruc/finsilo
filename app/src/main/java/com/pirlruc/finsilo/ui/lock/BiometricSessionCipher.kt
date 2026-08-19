package com.pirlruc.finsilo.ui.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * UI-lock CryptoObject only. Success here does not unwrap SQLCipher
 * (FS-027: biometric never opens the ledger on a cold process).
 */
internal object BiometricSessionCipher {
    private const val KEY_NAME: String = "finsilo_biometric_ui"
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
    private val confirmPayload: ByteArray = byteArrayOf(0x46, 0x53)

    fun cryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun confirm(result: BiometricPrompt.AuthenticationResult): Boolean {
        val cipher = result.cryptoObject?.cipher ?: return false
        return runCatching { cipher.doFinal(confirmPayload) }.isSuccess
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_NAME, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }
}
