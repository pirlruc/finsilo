package com.pirlruc.finsilo.ui.lock

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricPrompt
import com.pirlruc.finsilo.data.security.KeystoreAesGcmKey
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the SQLCipher passphrase with a Keystore AES key that requires strong
 * biometrics on every use. PIN and recovery wraps stay independent.
 */
internal object BiometricKeyWrap {
    private const val KEY_NAME: String = "finsilo_biometric_db"
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128

    fun encryptObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        initOrReplace { cipher.init(Cipher.ENCRYPT_MODE, it) }
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun decryptObject(blob: ByteArray): BiometricPrompt.CryptoObject {
        require(blob.size > IV_BYTES) { "Biometric wrap is truncated." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES))
        try {
            cipher.init(Cipher.DECRYPT_MODE, KeystoreAesGcmKey.getOrCreate(KEY_NAME), spec)
        } catch (error: GeneralSecurityException) {
            if (!isStaleBiometricKey(error)) throw error
            KeystoreAesGcmKey.delete(KEY_NAME)
            throw IllegalStateException(BIOMETRIC_KEY_RESET)
        }
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
    }

    private fun initOrReplace(init: (SecretKey) -> Unit) {
        try {
            init(KeystoreAesGcmKey.getOrCreate(KEY_NAME))
        } catch (error: GeneralSecurityException) {
            if (!isStaleBiometricKey(error)) throw error
            KeystoreAesGcmKey.delete(KEY_NAME)
            init(KeystoreAesGcmKey.getOrCreate(KEY_NAME))
        }
    }
}

internal const val BIOMETRIC_KEY_RESET: String = "Biometrics changed. Enter PIN."

internal fun isStaleBiometricKey(error: GeneralSecurityException): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is KeyPermanentlyInvalidatedException || current is UserNotAuthenticatedException) return true
        current = current.cause
    }
    return false
}
