package com.pirlruc.finsilo.domain.lock

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM wrap of the SQLCipher passphrase using a PIN or recovery secret.
 *
 * Layout: 16-byte PBKDF2 salt + 12-byte IV + ciphertext (includes the GCM tag).
 * The wrapping key is [AppLockCrypto.hashSecret], the same KDF as the app lock.
 * Wrong secrets and truncated blobs return null; they never wipe stored data.
 */
object PassphraseWrap {
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128
    private const val MIN_SIZE: Int = AppLockCrypto.SALT_BYTES + IV_BYTES + 16

    /** Encrypt [plaintext] with a wrapping key derived from [secret]. */
    fun wrap(secret: String, plaintext: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = AppLockCrypto.generateSalt(random)
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(secret, salt), GCMParameterSpec(TAG_BITS, iv))
        return salt + iv + cipher.doFinal(plaintext)
    }

    /** Decrypt [blob]; null when the secret is wrong or the bytes are truncated. */
    fun unwrap(secret: String, blob: ByteArray): ByteArray? {
        if (blob.size < MIN_SIZE) return null
        val saltEnd = AppLockCrypto.SALT_BYTES
        val ivEnd = saltEnd + IV_BYTES
        return decrypt(
            secret,
            blob.copyOfRange(0, saltEnd),
            blob.copyOfRange(saltEnd, ivEnd),
            blob.copyOfRange(ivEnd, blob.size),
        )
    }

    private fun decrypt(secret: String, salt: ByteArray, iv: ByteArray, body: ByteArray): ByteArray? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(secret, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun wrappingKey(secret: String, salt: ByteArray): SecretKeySpec = SecretKeySpec(AppLockCrypto.hashSecret(secret, salt), "AES")
}
