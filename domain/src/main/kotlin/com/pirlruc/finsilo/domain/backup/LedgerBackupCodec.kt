package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted portable snapshot. Not plaintext SQLite.
 *
 * Layout: magic `FSILO1` + 16-byte salt + 12-byte IV + AES-256-GCM ciphertext of a
 * tab-separated ledger. The wrapping key is PBKDF2 of the recovery code the user
 * typed at export, so a new phone can restore after unlock.
 */
object LedgerBackupCodec {
    private val magic = byteArrayOf(0x46, 0x53, 0x49, 0x4C, 0x4F, 0x31)
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128
    private const val MIN_SIZE: Int = 6 + AppLockCrypto.SALT_BYTES + IV_BYTES + 16

    /** Encrypt [snapshot] and [extras] with a recovery-code passphrase. */
    fun encrypt(
        snapshot: PortfolioSnapshot,
        passphrase: String,
        extras: LedgerBackupExtras = LedgerBackupExtras(),
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = AppLockCrypto.generateSalt(random)
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(LedgerBackupText.encode(snapshot, extras).toByteArray(Charsets.UTF_8))
        return magic + salt + iv + body
    }

    /** Decrypt [bytes]; truncated or corrupt files are [LedgerBackupResult.Refused]. */
    fun decrypt(bytes: ByteArray, passphrase: String): LedgerBackupResult {
        if (bytes.size < MIN_SIZE) return LedgerBackupResult.Refused("Backup file is truncated.")
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            return LedgerBackupResult.Refused("Not a FinSilo backup.")
        }
        val saltEnd = magic.size + AppLockCrypto.SALT_BYTES
        val ivEnd = saltEnd + IV_BYTES
        val salt = bytes.copyOfRange(magic.size, saltEnd)
        val iv = bytes.copyOfRange(saltEnd, ivEnd)
        val body = bytes.copyOfRange(ivEnd, bytes.size)
        return openBody(body, passphrase, salt, iv)
    }

    private fun openBody(body: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): LedgerBackupResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val plain =
            try {
                cipher.doFinal(body).toString(Charsets.UTF_8)
            } catch (_: AEADBadTagException) {
                return LedgerBackupResult.Refused("Wrong recovery code or corrupt backup.")
            } catch (_: Exception) {
                return LedgerBackupResult.Refused("Could not decrypt backup.")
            }
        return LedgerBackupText.decode(plain)
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val secret = AppLockCrypto.hashSecret(AppLockCrypto.normalizeRecovery(passphrase), salt)
        return SecretKeySpec(secret, "AES")
    }
}
