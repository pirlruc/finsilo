package com.pirlruc.finsilo.ui.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Shared Android Keystore AES-256-GCM key bootstrap for biometric wraps. */
internal object KeystoreAesGcmKey {
    private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"

    fun getOrCreate(
        alias: String,
        userAuthenticationRequired: Boolean = true,
        invalidatedByBiometricEnrollment: Boolean = true,
    ): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
        if (userAuthenticationRequired) {
            builder.setUserAuthenticationRequired(true)
        }
        if (invalidatedByBiometricEnrollment) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    fun delete(alias: String) {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
}
