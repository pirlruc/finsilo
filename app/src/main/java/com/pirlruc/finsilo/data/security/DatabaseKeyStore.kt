package com.pirlruc.finsilo.data.security

import android.content.Context
import android.content.SharedPreferences
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PassphraseWrap
import java.security.SecureRandom

/**
 * PIN/recovery wrap of the SQLCipher passphrase, plus Keystore-backed extras
 * such as the Alpha Vantage key.
 *
 * The unwrapped 32-byte database key stays in process memory after a successful
 * PIN or recovery unlock. UI re-lock does not evict it. A cold process cannot
 * open the ledger until the user types PIN or recovery again.
 */
interface LedgerKeySession {
    fun isSessionOpen(): Boolean

    fun needsWrapUpgrade(): Boolean

    fun provision(pin: String, recovery: String): Boolean

    fun unlockWithPin(pin: String): Boolean

    fun unlockWithRecovery(recovery: String): Boolean

    fun rewrapPin(newPin: String): Boolean

    fun rewrapRecovery(newRecovery: String): Boolean

    fun finishLegacyMigration(pin: String, recovery: String): Boolean
}

class DatabaseKeyStore(private val prefs: SharedPreferences) : LedgerKeySession {
    @Volatile
    private var session: ByteArray? = null

    fun alphaVantageKey(): String? = prefs.getString(KEY_ALPHA_VANTAGE, null)?.takeIf { it.isNotBlank() }

    fun setAlphaVantageKey(key: String) {
        prefs.edit().putString(KEY_ALPHA_VANTAGE, key.trim()).apply()
    }

    /** Copy of the unwrapped SQLCipher key; throws when this process has not unlocked. */
    fun sessionPassphrase(): ByteArray = session?.copyOf() ?: error("SQLCipher passphrase is not unwrapped in this process.")

    override fun isSessionOpen(): Boolean = session != null

    override fun needsWrapUpgrade(): Boolean = hasLegacyPassphrase() && !hasWrappedPassphrase()

    override fun provision(pin: String, recovery: String): Boolean {
        if (session != null) return true
        if (hasWrappedPassphrase() || hasLegacyPassphrase()) return false
        val key = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        if (!persistWraps(pin, recovery, key)) return false
        session = key
        return true
    }

    override fun unlockWithPin(pin: String): Boolean {
        if (session != null) return true
        val wrapped = storedBlob(KEY_WRAP_PIN)
        if (wrapped != null) return acceptWrapped(PassphraseWrap.unwrap(pin, wrapped))
        return unlockLegacy()
    }

    override fun unlockWithRecovery(recovery: String): Boolean {
        if (session != null) return true
        val secret = AppLockCrypto.normalizeRecovery(recovery)
        val wrapped = storedBlob(KEY_WRAP_RECOVERY)
        if (wrapped != null) return acceptWrapped(PassphraseWrap.unwrap(secret, wrapped))
        return unlockLegacy()
    }

    override fun rewrapPin(newPin: String): Boolean = rewrap(KEY_WRAP_PIN, newPin)

    override fun rewrapRecovery(newRecovery: String): Boolean = rewrap(KEY_WRAP_RECOVERY, AppLockCrypto.normalizeRecovery(newRecovery))

    override fun finishLegacyMigration(pin: String, recovery: String): Boolean {
        val key = session ?: return false
        if (!hasLegacyPassphrase()) return hasWrappedPassphrase()
        if (!persistWraps(pin, recovery, key)) return false
        // Confirm-screen path: wraps were just written; only then drop hex.
        return prefs.edit().remove(KEY_PASSPHRASE).commit()
    }

    fun hasLegacyPassphrase(): Boolean = prefs.contains(KEY_PASSPHRASE)

    fun hasWrappedPassphrase(): Boolean = prefs.contains(KEY_WRAP_PIN) && prefs.contains(KEY_WRAP_RECOVERY)

    private fun unlockLegacy(): Boolean {
        val hex = prefs.getString(KEY_PASSPHRASE, null) ?: return false
        val key = AppLockCrypto.fromHex(hex) ?: return false
        session = key
        return true
    }

    /**
     * Legacy hex is wiped only after a wrap already exists. The upgrade confirm
     * screen ([finishLegacyMigration]) is the path that creates wraps and then
     * deletes hex. This method must not be that path: leftover hex next to
     * existing wraps is stale, so a successful PIN/recovery unwrap may delete it.
     */
    private fun acceptWrapped(key: ByteArray?): Boolean {
        if (key == null) return false
        session = key
        if (hasLegacyPassphrase()) {
            prefs.edit().remove(KEY_PASSPHRASE).commit()
        }
        return true
    }

    private fun persistWraps(pin: String, recovery: String, key: ByteArray): Boolean {
        val recoverySecret = AppLockCrypto.normalizeRecovery(recovery)
        return prefs.edit()
            .putString(KEY_WRAP_PIN, AppLockCrypto.toHex(PassphraseWrap.wrap(pin, key)))
            .putString(KEY_WRAP_RECOVERY, AppLockCrypto.toHex(PassphraseWrap.wrap(recoverySecret, key)))
            .commit()
    }

    private fun rewrap(prefsKey: String, secret: String): Boolean {
        val key = session ?: return false
        return prefs.edit().putString(prefsKey, AppLockCrypto.toHex(PassphraseWrap.wrap(secret, key))).commit()
    }

    private fun storedBlob(prefsKey: String): ByteArray? {
        val hex = prefs.getString(prefsKey, null) ?: return null
        return AppLockCrypto.fromHex(hex)
    }

    companion object {
        private const val PREFS_FILE = "finsilo_secure"
        private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
        private const val KEY_WRAP_PIN = "sqlcipher_wrap_pin"
        private const val KEY_WRAP_RECOVERY = "sqlcipher_wrap_recovery"
        private const val KEY_ALPHA_VANTAGE = "alpha_vantage_key"
        private const val PASSPHRASE_BYTES: Int = 32

        fun create(context: Context): DatabaseKeyStore = DatabaseKeyStore(SecurePreferences.open(context, PREFS_FILE))
    }
}
