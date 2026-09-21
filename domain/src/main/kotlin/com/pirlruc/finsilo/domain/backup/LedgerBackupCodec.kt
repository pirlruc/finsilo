package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.lock.AesGcmPassphrase
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import java.security.SecureRandom

/**
 * Encrypted portable snapshot. Not plaintext SQLite.
 *
 * Layout: magic `FSILO1` + 16-byte salt + 12-byte IV + AES-256-GCM ciphertext of a
 * tab-separated ledger. The wrapping key is PBKDF2 of the recovery code the user
 * typed at export, so a new phone can restore after unlock.
 */
object LedgerBackupCodec {
    private val magic = byteArrayOf(0x46, 0x53, 0x49, 0x4C, 0x4F, 0x31)
    private const val MIN_SIZE: Int = 6 + AppLockCrypto.SALT_BYTES + AesGcmPassphrase.IV_BYTES + 16

    /** Encrypt [snapshot] and [extras] with a recovery-code passphrase. */
    fun encrypt(
        snapshot: PortfolioSnapshot,
        passphrase: String,
        extras: LedgerBackupExtras = LedgerBackupExtras(),
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val plain = LedgerBackupText.encode(snapshot, extras).toByteArray(Charsets.UTF_8)
        return AesGcmPassphrase.seal(AppLockCrypto.normalizeRecovery(passphrase), plain, random, magic)
    }

    /** Decrypt [bytes]; truncated or corrupt files are [LedgerBackupResult.Refused]. */
    fun decrypt(bytes: ByteArray, passphrase: String): LedgerBackupResult {
        if (bytes.size < MIN_SIZE) return LedgerBackupResult.Refused("Backup file is truncated.")
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            return LedgerBackupResult.Refused("Not a FinSilo backup.")
        }
        val plain = AesGcmPassphrase.open(AppLockCrypto.normalizeRecovery(passphrase), bytes, magic.size)
            ?: return LedgerBackupResult.Refused("Wrong recovery code or corrupt backup.")
        return LedgerBackupText.decode(plain.toString(Charsets.UTF_8))
    }
}
