package com.pirlruc.finsilo.domain.lock

import java.security.SecureRandom

/**
 * AES-256-GCM wrap of the SQLCipher passphrase using a PIN or recovery secret.
 *
 * Layout: 16-byte PBKDF2 salt + 12-byte IV + ciphertext (includes the GCM tag).
 * The wrapping key is [AppLockCrypto.hashSecret], the same KDF as the app lock.
 * Wrong secrets and truncated blobs return null; they never wipe stored data.
 */
object PassphraseWrap {
    /** Encrypt [plaintext] with a wrapping key derived from [secret]. */
    fun wrap(secret: String, plaintext: ByteArray, random: SecureRandom = SecureRandom()): ByteArray =
        AesGcmPassphrase.seal(secret, plaintext, random)

    /** Decrypt [blob]; null when the secret is wrong or the bytes are truncated. */
    fun unwrap(secret: String, blob: ByteArray): ByteArray? = AesGcmPassphrase.open(secret, blob)
}
