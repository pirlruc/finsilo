package com.pirlruc.finsilo.domain.lock

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM of a passphrase-derived key.
 *
 * Layout after the optional prefix: 16-byte PBKDF2 salt, 12-byte IV, ciphertext with GCM tag.
 * Callers choose the prefix (none for SQLCipher wraps, `FSILO1` for backups) and whether
 * [secret] is already normalized.
 */
internal object AesGcmPassphrase {
    const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128

    fun seal(secret: String, plaintext: ByteArray, random: SecureRandom, prefix: ByteArray = byteArrayOf()): ByteArray {
        val salt = AppLockCrypto.generateSalt(random)
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        return prefix + salt + iv + transform(Cipher.ENCRYPT_MODE, secret, salt, iv, plaintext)
    }

    fun open(secret: String, blob: ByteArray, prefixLength: Int = 0): ByteArray? {
        val min = prefixLength + AppLockCrypto.SALT_BYTES + IV_BYTES + 16
        if (blob.size < min) return null
        val saltEnd = prefixLength + AppLockCrypto.SALT_BYTES
        val ivEnd = saltEnd + IV_BYTES
        return try {
            transform(
                Cipher.DECRYPT_MODE,
                secret,
                blob.copyOfRange(prefixLength, saltEnd),
                blob.copyOfRange(saltEnd, ivEnd),
                blob.copyOfRange(ivEnd, blob.size),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun transform(mode: Int, secret: String, salt: ByteArray, iv: ByteArray, body: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, wrappingKey(secret, salt), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    private fun wrappingKey(secret: String, salt: ByteArray): SecretKeySpec {
        val bits = AppLockCrypto.hashSecret(secret, salt)
        return try {
            SecretKeySpec(bits, "AES")
        } finally {
            bits.fill(0)
        }
    }
}
