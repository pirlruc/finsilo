package com.pirlruc.finsilo.data.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption used to seal SharedPreferences values. */
internal interface PrefsAead {
    fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray

    fun open(associatedData: ByteArray, blob: ByteArray): ByteArray?
}

/**
 * AES-256-GCM with a length-prefixed IV.
 *
 * Associated data is bound with `Cipher.updateAAD` and is not stored in the blob.
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
            // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
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

/** Keystore alias per build type. Debug never reads the release alias. */
internal object PrefsKeyAlias {
    const val LEGACY: String = "com.pirlruc.finsilo.prefs_aes256_gcm"
    const val DEBUG: String = "com.pirlruc.finsilo.prefs_aes256_gcm.debug"
    const val RELEASE: String = "com.pirlruc.finsilo.prefs_aes256_gcm.release"

    fun current(debug: Boolean): String = if (debug) DEBUG else RELEASE

    fun readable(debug: Boolean): List<String> = listOf(current(debug), LEGACY)
}

/** Reads the legacy alias when the build-type key cannot open a value, then seals with the current key. */
internal class MigratingPrefsAead(private val current: PrefsAead, private val legacy: PrefsAead?) : PrefsAead {
    override fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray = current.seal(associatedData, plaintext)

    override fun open(associatedData: ByteArray, blob: ByteArray): ByteArray? =
        current.open(associatedData, blob) ?: legacy?.open(associatedData, blob)

    fun reseal(associatedData: ByteArray, blob: ByteArray, plaintext: ByteArray): ByteArray? {
        if (current.open(associatedData, blob) != null) return null
        if (legacy?.open(associatedData, blob) == null) return null
        return current.seal(associatedData, plaintext)
    }
}

/** Android Keystore AES-256-GCM key for [SecurePreferences], not the Tink master-key alias. */
internal object AndroidPrefsKeystore {
    const val ALIAS = PrefsKeyAlias.LEGACY

    fun aead(debug: Boolean): PrefsAead {
        val current = AesGcmPrefsAead(key(PrefsKeyAlias.current(debug)))
        val legacyKey = KeystoreAesGcmKey.find(PrefsKeyAlias.LEGACY) ?: return current
        return MigratingPrefsAead(current, AesGcmPrefsAead(legacyKey))
    }

    private fun key(alias: String): SecretKey = KeystoreAesGcmKey.getOrCreate(
        alias,
        userAuthenticationRequired = false,
        invalidatedByBiometricEnrollment = false,
    )
}

internal class AndroidKeystoreAesGcmAead(debug: Boolean = com.pirlruc.finsilo.BuildConfig.DEBUG) : PrefsAead {
    private val inner = AndroidPrefsKeystore.aead(debug)

    override fun seal(associatedData: ByteArray, plaintext: ByteArray): ByteArray = inner.seal(associatedData, plaintext)

    override fun open(associatedData: ByteArray, blob: ByteArray): ByteArray? = inner.open(associatedData, blob)
}
