package com.pirlruc.finsilo.ui.lock

import androidx.biometric.BiometricPrompt
import com.pirlruc.finsilo.data.security.KeystoreAesGcmKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the SQLCipher passphrase with a Keystore AES key that requires strong
 * biometrics on every use. PIN and recovery wraps stay independent.
 */
internal object BiometricKeyWrap {
    private const val KEY_NAME: String = "finsilo_biometric_db_v2"
    private const val LEGACY_KEY_NAME: String = "finsilo_biometric_db"
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128

    fun encryptObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, KeystoreAesGcmKey.getOrCreate(KEY_NAME))
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun decryptObject(blob: ByteArray): BiometricPrompt.CryptoObject {
        require(blob.size > IV_BYTES) { "Biometric wrap is truncated." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            KeystoreAesGcmKey.getOrCreate(KEY_NAME),
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
        )
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun seal(result: BiometricPrompt.AuthenticationResult, plaintext: ByteArray): ByteArray? {
        val cipher = result.cryptoObject?.cipher ?: return null
        val iv = cipher.iv ?: return null
        return runCatching { iv + cipher.doFinal(plaintext) }.getOrNull()
    }

    fun open(result: BiometricPrompt.AuthenticationResult, blob: ByteArray): ByteArray? {
        if (blob.size <= IV_BYTES) return null
        val cipher = result.cryptoObject?.cipher ?: return null
        return runCatching { cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)) }.getOrNull()
    }

    fun deleteKey() {
        KeystoreAesGcmKey.delete(KEY_NAME)
        KeystoreAesGcmKey.delete(LEGACY_KEY_NAME)
    }
}
