package com.pirlruc.finsilo.ui.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the SQLCipher passphrase with a Keystore AES key that requires strong
 * biometrics on every use. PIN and recovery wraps stay independent.
 */
internal object BiometricKeyWrap {
    private const val KEY_NAME: String = "finsilo_biometric_db"
    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128

    fun encryptObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun decryptObject(blob: ByteArray): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)))
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
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(KEY_NAME)) store.deleteEntry(KEY_NAME)
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
