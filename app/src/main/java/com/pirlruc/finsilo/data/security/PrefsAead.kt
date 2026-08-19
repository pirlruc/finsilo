package com.pirlruc.finsilo.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption used to seal SharedPreferences values. */
internal interface PrefsAead {
    fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray

    fun open(associatedData: ByteArray, blob: ByteArray): ByteArray?
}

/**
 * AES-256-GCM with a length-prefixed IV and AAD.
 *
 * Wire format: `ivLen (1 byte) || iv || ciphertext+tag`.
 */
internal class AesGcmPrefsAead(private val key: SecretKey) : PrefsAead {
    override fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    override fun open(associatedData: ByteArray, blob: ByteArray): ByteArray? {
        val iv = ivOf(blob) ?: return null
        val ciphertext = blob.copyOfRange(1 + iv.size, blob.size)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val MIN_IV_BYTES = 8
        private const val MAX_IV_BYTES = 16

        private fun ivOf(blob: ByteArray): ByteArray? {
            if (blob.isEmpty()) return null
            val ivLen = blob[0].toInt() and 0xFF
            if (ivLen < MIN_IV_BYTES || ivLen > MAX_IV_BYTES) return null
            val minSize = 1 + ivLen + (TAG_BITS / 8)
            if (blob.size < minSize) return null
            return blob.copyOfRange(1, 1 + ivLen)
        }
    }
}

/** Android Keystore AES-256-GCM key for [SecurePreferences], not the Tink master-key alias. */
internal object AndroidPrefsKeystore {
    const val ALIAS = "com.pirlruc.finsilo.prefs_aes256_gcm"

    fun getOrCreateKey(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        synchronized(this) {
            keystore.load(null)
            (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

internal class AndroidKeystoreAesGcmAead : PrefsAead {
    private val inner = AesGcmPrefsAead(AndroidPrefsKeystore.getOrCreateKey())

    override fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray = inner.seal(associatedData, plaintext)

    override fun open(associatedData: ByteArray, blob: ByteArray): ByteArray? = inner.open(associatedData, blob)
}
