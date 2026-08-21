package com.pirlruc.finsilo.domain.lock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN and recovery-code hashing for the on-device app lock.
 *
 * Only salt + hash are stored. The PIN cannot be reconstructed from the hash;
 * the recovery code is shown once at setup and later resets the PIN.
 * PBKDF2-HMAC-SHA256 uses 210_000 iterations (above typical SAST floors).
 */
object AppLockCrypto {
    const val PIN_MIN_LENGTH: Int = 4
    const val PIN_MAX_LENGTH: Int = 8
    const val ITERATIONS: Int = 210_000
    const val KEY_LENGTH_BITS: Int = 256
    const val SALT_BYTES: Int = 16
    const val RECOVERY_BYTES: Int = 16

    /** Cryptographic salt for one stored secret. */
    fun generateSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** Grouped uppercase hex recovery code (16 bytes). */
    fun generateRecoveryCode(random: SecureRandom = SecureRandom()): String {
        val raw = ByteArray(RECOVERY_BYTES).also { random.nextBytes(it) }
        return try {
            toHex(raw).chunked(4).joinToString("-").uppercase()
        } finally {
            raw.fill(0)
        }
    }

    /** True when [pin] is 4–8 digits. */
    fun pinOk(pin: String): Boolean = pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it.isDigit() }

    /** PBKDF2-HMAC-SHA256 of [secret] with [salt]. */
    fun hashSecret(secret: String, salt: ByteArray): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    /** Constant-time compare of [secret] against a stored hash. */
    fun verify(secret: String, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = hashSecret(secret, salt)
        return try {
            MessageDigest.isEqual(actual, expected)
        } finally {
            actual.fill(0)
        }
    }

    /** Strips grouping punctuation so typed recovery codes still match. */
    fun normalizeRecovery(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase()

    /** Lowercase hex encoding used in encrypted prefs. */
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { byte -> "%02x".format(byte) }

    /** Inverse of [toHex]; null when the string is not even-length hex. */
    fun fromHex(hex: String): ByteArray? {
        val trimmed = hex.trim()
        if (trimmed.length % 2 != 0 || trimmed.isEmpty()) return null
        return runCatching {
            ByteArray(trimmed.length / 2) { index ->
                trimmed.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
