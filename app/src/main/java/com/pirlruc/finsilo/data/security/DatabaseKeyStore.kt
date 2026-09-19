package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PassphraseWrap
import java.security.SecureRandom

/**
 * PIN/recovery wrap of the SQLCipher passphrase, plus Keystore-backed extras
 * such as the Alpha Vantage key.
 *
 * The unwrapped 32-byte database key stays in process memory after a successful
 * PIN, recovery, or biometric unwrap. Overlay lock does not evict it; [evictSession]
 * wipes it after the hybrid background grace.
 */
interface LedgerKeySession {
    fun isSessionOpen(): Boolean

    fun sessionKeyOrNull(): ByteArray?

    fun needsWrapUpgrade(): Boolean

    fun provision(pin: String, recovery: String): Boolean

    fun unlockWithPin(pin: String): Boolean

    fun unlockWithRecovery(recovery: String): Boolean

    fun unlockWithUnwrappedKey(key: ByteArray): Boolean

    fun rewrapPin(newPin: String): Boolean

    fun rewrapRecovery(newRecovery: String): Boolean

    fun finishLegacyMigration(pin: String, recovery: String): Boolean

    fun biometricWrapBlob(): ByteArray?

    fun persistBiometricWrap(blob: ByteArray?): Boolean

    fun evictSession()

    fun discardOrphanWraps(): Boolean

    fun rollbackLastWrap(): Boolean
}

class DatabaseKeyStore(private val prefs: SharedPreferences) : LedgerKeySession {
    @Volatile
    private var session: ByteArray? = null

    private var lastWrapUndo: (SharedPreferences.Editor.() -> Unit)? = null

    fun alphaVantageKey(): String? = prefs.getString(KEY_ALPHA_VANTAGE, null)?.takeIf { it.isNotBlank() }

    fun setAlphaVantageKey(key: String): Boolean = prefs.edit().putString(KEY_ALPHA_VANTAGE, key.trim()).commit()

    /** Copy of the unwrapped SQLCipher key; throws when this process has not unlocked. */
    fun sessionPassphrase(): ByteArray = session?.copyOf() ?: error("SQLCipher passphrase is not unwrapped in this process.")

    override fun isSessionOpen(): Boolean = session != null

    override fun evictSession() {
        session?.fill(0)
        session = null
    }

    override fun sessionKeyOrNull(): ByteArray? = session?.copyOf()

    override fun needsWrapUpgrade(): Boolean = hasLegacyPassphrase() && !hasWrappedPassphrase()

    override fun provision(pin: String, recovery: String): Boolean =
        provisionKey { persistWraps(pin, recovery, it) }

    fun provisionWithLock(lock: AppLockStore, pin: String, recovery: String): Boolean =
        provisionKey {
            persistWraps(pin, recovery, it) { lock.applySetup(this, pin, recovery, biometric = false) }
        }

    override fun discardOrphanWraps(): Boolean {
        if (session != null) return true
        return prefs.edit()
            .remove(KEY_WRAP_PIN)
            .remove(KEY_WRAP_RECOVERY)
            .remove(KEY_WRAP_BIOMETRIC)
            .commit()
    }

    override fun rollbackLastWrap(): Boolean {
        val undo = lastWrapUndo ?: return false
        lastWrapUndo = null
        val editor = prefs.edit()
        editor.undo()
        return editor.commit()
    }

    override fun unlockWithPin(pin: String): Boolean = unlockWithSecret(pin, KEY_WRAP_PIN)

    override fun unlockWithRecovery(recovery: String): Boolean =
        unlockWithSecret(AppLockCrypto.normalizeRecovery(recovery), KEY_WRAP_RECOVERY)

    override fun unlockWithUnwrappedKey(key: ByteArray): Boolean {
        if (key.size != PASSPHRASE_BYTES) return false
        return acceptWrapped(key.copyOf())
    }

    override fun biometricWrapBlob(): ByteArray? = storedBlob(KEY_WRAP_BIOMETRIC)

    override fun persistBiometricWrap(blob: ByteArray?): Boolean {
        val editor = prefs.edit()
        if (blob == null) {
            editor.remove(KEY_WRAP_BIOMETRIC)
        } else {
            editor.putString(KEY_WRAP_BIOMETRIC, AppLockCrypto.toHex(blob))
        }
        return editor.commit()
    }

    override fun rewrapPin(newPin: String): Boolean = rewrap(KEY_WRAP_PIN, newPin)

    override fun rewrapRecovery(newRecovery: String): Boolean =
        rewrap(KEY_WRAP_RECOVERY, AppLockCrypto.normalizeRecovery(newRecovery))

    override fun finishLegacyMigration(pin: String, recovery: String): Boolean = migrateLegacy(pin, recovery)

    fun finishLegacyMigrationWithRecoveryHash(lock: AppLockStore, pin: String, recovery: String): Boolean =
        migrateLegacy(pin, recovery) { lock.applyRecoveryHash(this, recovery) }

    fun finishLegacyMigrationWithPinHash(lock: AppLockStore, newPin: String, recovery: String): Boolean =
        migrateLegacy(newPin, recovery) { lock.applyPinReset(this, newPin) }

    fun rewrapRecoveryWithLock(lock: AppLockStore, recovery: String): Boolean =
        rewrap(KEY_WRAP_RECOVERY, AppLockCrypto.normalizeRecovery(recovery)) {
            lock.applyRecoveryHash(this, recovery)
        }

    fun rewrapPinWithLock(lock: AppLockStore, newPin: String): Boolean =
        rewrap(KEY_WRAP_PIN, newPin) { lock.applyPinReset(this, newPin) }

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

    private fun provisionKey(persist: (ByteArray) -> Boolean): Boolean {
        val existing = session
        if (existing != null) return persist(existing)
        if (hasWrappedPassphrase() || hasLegacyPassphrase()) return false
        val key = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val stored = persist(key)
        if (stored) session = key
        return stored
    }

    private fun unlockWithSecret(secret: String, wrapKey: String): Boolean {
        val hex = prefs.getString(wrapKey, null) ?: return unlockWithoutWrap()
        val wrapped = AppLockCrypto.fromHex(hex) ?: return false
        if (session != null) return pinMatchesWrap(secret, wrapped)
        return acceptWrapped(PassphraseWrap.unwrap(secret, wrapped))
    }

    private fun unlockWithoutWrap(): Boolean = session != null || unlockLegacy()

    private fun persistWraps(
        pin: String,
        recovery: String,
        key: ByteArray,
        extra: SharedPreferences.Editor.() -> Unit = {},
    ): Boolean {
        val editor = prefs.edit()
        applyWraps(editor, pin, recovery, key)
        editor.extra()
        return editor.commit()
    }

    private fun migrateLegacy(
        pin: String,
        recovery: String,
        extra: SharedPreferences.Editor.() -> Unit = {},
    ): Boolean {
        val key = session ?: return false
        if (!hasLegacyPassphrase()) return hasWrappedPassphrase()
        rememberWrapState()
        val editor = prefs.edit()
        applyWraps(editor, pin, recovery, key)
        editor.remove(KEY_PASSPHRASE)
        editor.extra()
        return editor.commit()
    }

    private fun applyWraps(editor: SharedPreferences.Editor, pin: String, recovery: String, key: ByteArray) {
        val recoverySecret = AppLockCrypto.normalizeRecovery(recovery)
        editor.putString(KEY_WRAP_PIN, AppLockCrypto.toHex(PassphraseWrap.wrap(pin, key)))
        editor.putString(KEY_WRAP_RECOVERY, AppLockCrypto.toHex(PassphraseWrap.wrap(recoverySecret, key)))
    }

    private fun rewrap(
        prefsKey: String,
        secret: String,
        extra: SharedPreferences.Editor.() -> Unit = {},
    ): Boolean {
        val key = session ?: return false
        rememberKey(prefsKey)
        val editor = prefs.edit()
        applyRewrap(editor, prefsKey, secret, key)
        editor.extra()
        return editor.commit()
    }

    private fun applyRewrap(editor: SharedPreferences.Editor, prefsKey: String, secret: String, key: ByteArray) {
        editor.putString(prefsKey, AppLockCrypto.toHex(PassphraseWrap.wrap(secret, key)))
    }

    private fun pinMatchesWrap(secret: String, wrapped: ByteArray): Boolean {
        val unwrapped = PassphraseWrap.unwrap(secret, wrapped) ?: return false
        unwrapped.fill(0)
        return true
    }

    private fun rememberWrapState() {
        val hex = prefs.getString(KEY_PASSPHRASE, null)
        val pinWrap = prefs.getString(KEY_WRAP_PIN, null)
        val recoveryWrap = prefs.getString(KEY_WRAP_RECOVERY, null)
        val biometricWrap = prefs.getString(KEY_WRAP_BIOMETRIC, null)
        lastWrapUndo = {
            restore(KEY_PASSPHRASE, hex)
            restore(KEY_WRAP_PIN, pinWrap)
            restore(KEY_WRAP_RECOVERY, recoveryWrap)
            restore(KEY_WRAP_BIOMETRIC, biometricWrap)
        }
    }

    private fun rememberKey(prefsKey: String) {
        val previous = prefs.getString(prefsKey, null)
        lastWrapUndo = { restore(prefsKey, previous) }
    }

    private fun SharedPreferences.Editor.restore(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    private fun storedBlob(prefsKey: String): ByteArray? {
        val hex = prefs.getString(prefsKey, null) ?: return null
        return AppLockCrypto.fromHex(hex)
    }

    companion object {
        private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
        private const val KEY_WRAP_PIN = "sqlcipher_wrap_pin"
        private const val KEY_WRAP_RECOVERY = "sqlcipher_wrap_recovery"
        private const val KEY_WRAP_BIOMETRIC = "sqlcipher_wrap_biometric"
        private const val KEY_ALPHA_VANTAGE = "alpha_vantage_key"
        private const val PASSPHRASE_BYTES: Int = 32
    }
}
