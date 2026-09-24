package com.pirlruc.finsilo.ui.lock

import androidx.biometric.BiometricPrompt
import com.pirlruc.finsilo.data.security.KeystoreAesGcmKey
import javax.crypto.Cipher

/**
 * UI-lock CryptoObject only. Success here does not unwrap SQLCipher
 * (FS-027: biometric never opens the ledger on a cold process).
 */
internal object BiometricSessionCipher {
    private const val KEY_NAME: String = "finsilo_biometric_ui_v2"
    private const val LEGACY_KEY_NAME: String = "finsilo_biometric_ui"
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private val confirmPayload: ByteArray = byteArrayOf(0x46, 0x53)

    fun cryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, KeystoreAesGcmKey.getOrCreate(KEY_NAME))
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun confirm(result: BiometricPrompt.AuthenticationResult): Boolean {
        val cipher = result.cryptoObject?.cipher ?: return false
        return runCatching { cipher.doFinal(confirmPayload) }.isSuccess
    }

    fun deleteKey() {
        KeystoreAesGcmKey.delete(KEY_NAME)
        KeystoreAesGcmKey.delete(LEGACY_KEY_NAME)
    }
}
