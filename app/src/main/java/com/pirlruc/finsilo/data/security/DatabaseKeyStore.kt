package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PassphraseWrap
import com.pirlruc.finsilo.domain.performance.FireAssumptions
import com.pirlruc.finsilo.domain.vault.VaultKeys
import com.pirlruc.finsilo.domain.vault.VaultSelection
import java.math.BigDecimal
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

class DatabaseKeyStore(private val prefs: SharedPreferences, private val selection: VaultSelection = VaultSelection()) : LedgerKeySession {
    @Volatile
    private var session: ByteArray? = null

    private var lastWrapUndo: (SharedPreferences.Editor.() -> Unit)? = null

    fun alphaVantageKey(): String? = text(KEY_ALPHA_VANTAGE)

    fun setAlphaVantageKey(key: String): Boolean = prefs.edit().putString(wrap(KEY_ALPHA_VANTAGE), key.trim()).commitObserved()

    fun trading212SyncEnabled(): Boolean = prefs.getBoolean(wrap(KEY_T212_SYNC), false)

    fun setTrading212SyncEnabled(enabled: Boolean): Boolean = prefs.edit().putBoolean(wrap(KEY_T212_SYNC), enabled).commitObserved()

    fun trading212Key(): String? = text(KEY_T212_KEY)

    fun trading212Secret(): String? = text(KEY_T212_SECRET)

    fun trading212Status(): String? = text(KEY_T212_STATUS)

    fun setTrading212Status(message: String?): Boolean {
        val editor = prefs.edit()
        if (message.isNullOrBlank()) editor.remove(wrap(KEY_T212_STATUS)) else editor.putString(wrap(KEY_T212_STATUS), message)
        return editor.commitObserved()
    }

    fun setTrading212Credentials(key: String, secret: String): Boolean {
        val editor = prefs.edit()
        if (key.isNotBlank()) editor.putString(wrap(KEY_T212_KEY), key.trim())
        if (secret.isNotBlank()) editor.putString(wrap(KEY_T212_SECRET), secret.trim())
        return editor.commitObserved()
    }

    fun fireAssumptions(): FireAssumptions {
        val defaults = FireAssumptions.defaults()
        return FireAssumptions(
            annualExpensesEur = decimal(KEY_FIRE_EXPENSES, defaults.annualExpensesEur),
            withdrawalRate = decimal(KEY_FIRE_WITHDRAWAL, defaults.withdrawalRate),
            annualSavingsEur = decimal(KEY_FIRE_SAVINGS, defaults.annualSavingsEur),
            annualReturn = decimal(KEY_FIRE_RETURN, defaults.annualReturn),
        )
    }

    fun ibkrToken(): String? = text(KEY_IBKR_TOKEN)

    fun ibkrQueryId(): String? = text(KEY_IBKR_QUERY)

    fun setIbkr(token: String, queryId: String): Boolean = pair(KEY_IBKR_TOKEN, token, KEY_IBKR_QUERY, queryId)

    fun krakenKey(): String? = text(KEY_KRAKEN_KEY)

    fun krakenSecret(): String? = text(KEY_KRAKEN_SECRET)

    fun setKraken(key: String, secret: String): Boolean = pair(KEY_KRAKEN_KEY, key, KEY_KRAKEN_SECRET, secret)

    fun coinbaseKeyName(): String? = text(KEY_COINBASE_NAME)

    fun coinbaseKey(): String? = text(KEY_COINBASE_KEY)

    fun setCoinbase(name: String, key: String): Boolean = pair(KEY_COINBASE_NAME, name, KEY_COINBASE_KEY, key)

    fun webDavUrl(): String? = text(KEY_WEBDAV_URL)

    fun webDavUser(): String? = text(KEY_WEBDAV_USER)

    fun webDavPassword(): String? = text(KEY_WEBDAV_PASSWORD)

    fun webDavEtag(): String? = text(KEY_WEBDAV_ETAG)

    fun setWebDav(url: String, user: String, password: String): Boolean = prefs.edit()
        .putIfPresent(KEY_WEBDAV_URL, url)
        .putIfPresent(KEY_WEBDAV_USER, user)
        .putIfPresent(KEY_WEBDAV_PASSWORD, password)
        .commitObserved()

    fun brokerSyncStatus(): String? = text(KEY_BROKER_STATUS)

    fun setBrokerSyncStatus(message: String?): Boolean {
        val editor = prefs.edit()
        if (message.isNullOrBlank()) editor.remove(wrap(KEY_BROKER_STATUS)) else editor.putString(wrap(KEY_BROKER_STATUS), message)
        return editor.commitObserved()
    }

    fun openBbBaseUrl(): String? = text(KEY_OPENBB_URL)

    fun openBbToken(): String? = text(KEY_OPENBB_TOKEN)

    fun setOpenBb(url: String, token: String): Boolean {
        val editor = prefs.edit()
        if (url.isBlank()) editor.remove(wrap(KEY_OPENBB_URL)) else editor.putString(wrap(KEY_OPENBB_URL), url.trim())
        if (token.isBlank()) editor.remove(wrap(KEY_OPENBB_TOKEN)) else editor.putString(wrap(KEY_OPENBB_TOKEN), token.trim())
        return editor.commitObserved()
    }

    fun setWebDavEtag(etag: String?): Boolean {
        val editor = prefs.edit()
        if (etag.isNullOrBlank()) editor.remove(wrap(KEY_WEBDAV_ETAG)) else editor.putString(wrap(KEY_WEBDAV_ETAG), etag)
        return editor.commitObserved()
    }

    fun setFireAssumptions(value: FireAssumptions): Boolean = prefs.edit()
        .putString(wrap(KEY_FIRE_EXPENSES), value.annualExpensesEur.toPlainString())
        .putString(wrap(KEY_FIRE_WITHDRAWAL), value.withdrawalRate.toPlainString())
        .putString(wrap(KEY_FIRE_SAVINGS), value.annualSavingsEur.toPlainString())
        .putString(wrap(KEY_FIRE_RETURN), value.annualReturn.toPlainString())
        .commitObserved()

    /** Copy of the unwrapped SQLCipher key; throws when this process has not unlocked. */
    fun sessionPassphrase(): ByteArray = session?.copyOf() ?: error("SQLCipher passphrase is not unwrapped in this process.")

    override fun isSessionOpen(): Boolean = session != null

    override fun evictSession() {
        session?.fill(0)
        session = null
    }

    override fun sessionKeyOrNull(): ByteArray? = session?.copyOf()

    override fun needsWrapUpgrade(): Boolean = hasLegacyPassphrase() && !hasWrappedPassphrase()

    override fun provision(pin: String, recovery: String): Boolean = provisionKey { persistWraps(pin, recovery, it) }

    fun provisionWithLock(lock: AppLockStore, pin: String, recovery: String): Boolean = provisionKey {
        persistWraps(pin, recovery, it) { lock.applySetup(this, pin, recovery, biometric = false) }
    }

    override fun discardOrphanWraps(): Boolean {
        if (session != null) return true
        return prefs.edit()
            .remove(wrap(KEY_WRAP_PIN))
            .remove(wrap(KEY_WRAP_RECOVERY))
            .remove(wrap(KEY_WRAP_BIOMETRIC))
            .commitObserved()
    }

    override fun rollbackLastWrap(): Boolean {
        val undo = lastWrapUndo ?: return false
        lastWrapUndo = null
        val editor = prefs.edit()
        editor.undo()
        return editor.commitObserved()
    }

    override fun unlockWithPin(pin: String): Boolean = unlockWithSecret(pin, KEY_WRAP_PIN)

    override fun unlockWithRecovery(recovery: String): Boolean =
        unlockWithSecret(AppLockCrypto.normalizeRecovery(recovery), KEY_WRAP_RECOVERY)

    override fun unlockWithUnwrappedKey(key: ByteArray): Boolean {
        if (key.size != PASSPHRASE_BYTES) return false
        return acceptWrapped(key.copyOf())
    }

    override fun biometricWrapBlob(): ByteArray? = storedBlob(wrap(KEY_WRAP_BIOMETRIC))

    override fun persistBiometricWrap(blob: ByteArray?): Boolean {
        val editor = prefs.edit()
        if (blob == null) {
            editor.remove(wrap(KEY_WRAP_BIOMETRIC))
        } else {
            editor.putString(wrap(KEY_WRAP_BIOMETRIC), AppLockCrypto.toHex(blob))
        }
        return editor.commitObserved()
    }

    override fun rewrapPin(newPin: String): Boolean = rewrap(KEY_WRAP_PIN, newPin)

    override fun rewrapRecovery(newRecovery: String): Boolean = rewrap(KEY_WRAP_RECOVERY, AppLockCrypto.normalizeRecovery(newRecovery))

    override fun finishLegacyMigration(pin: String, recovery: String): Boolean = migrateLegacy(pin, recovery)

    fun finishLegacyMigrationWithRecoveryHash(lock: AppLockStore, pin: String, recovery: String): Boolean =
        migrateLegacy(pin, recovery) { lock.applyRecoveryHash(this, recovery) }

    fun finishLegacyMigrationWithPinHash(lock: AppLockStore, newPin: String, recovery: String): Boolean =
        migrateLegacy(newPin, recovery) { lock.applyPinReset(this, newPin) }

    fun rewrapRecoveryWithLock(lock: AppLockStore, recovery: String): Boolean =
        rewrap(KEY_WRAP_RECOVERY, AppLockCrypto.normalizeRecovery(recovery)) {
            lock.applyRecoveryHash(this, recovery)
        }

    fun rewrapPinWithLock(lock: AppLockStore, newPin: String): Boolean = rewrap(KEY_WRAP_PIN, newPin) { lock.applyPinReset(this, newPin) }

    fun hasLegacyPassphrase(): Boolean = prefs.contains(wrap(KEY_PASSPHRASE))

    fun hasWrappedPassphrase(): Boolean = prefs.contains(wrap(KEY_WRAP_PIN)) && prefs.contains(wrap(KEY_WRAP_RECOVERY))

    private fun unlockLegacy(): Boolean {
        val hex = prefs.getString(wrap(KEY_PASSPHRASE), null) ?: return false
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
            prefs.edit().remove(wrap(KEY_PASSPHRASE)).commitObserved()
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
        val hex = prefs.getString(wrap(wrapKey), null) ?: return unlockWithoutWrap()
        val wrapped = AppLockCrypto.fromHex(hex) ?: return false
        if (session != null) return pinMatchesWrap(secret, wrapped)
        return acceptWrapped(PassphraseWrap.unwrap(secret, wrapped))
    }

    private fun unlockWithoutWrap(): Boolean = session != null || unlockLegacy()

    private fun persistWraps(pin: String, recovery: String, key: ByteArray, extra: SharedPreferences.Editor.() -> Unit = {}): Boolean {
        val editor = prefs.edit()
        applyWraps(editor, pin, recovery, key)
        editor.extra()
        return editor.commitObserved()
    }

    private fun migrateLegacy(pin: String, recovery: String, extra: SharedPreferences.Editor.() -> Unit = {}): Boolean {
        val key = session ?: return false
        if (!hasLegacyPassphrase()) return hasWrappedPassphrase()
        rememberWrapState()
        val editor = prefs.edit()
        applyWraps(editor, pin, recovery, key)
        editor.remove(wrap(KEY_PASSPHRASE))
        editor.extra()
        return editor.commitObserved()
    }

    private fun applyWraps(editor: SharedPreferences.Editor, pin: String, recovery: String, key: ByteArray) {
        val recoverySecret = AppLockCrypto.normalizeRecovery(recovery)
        editor.putString(wrap(KEY_WRAP_PIN), AppLockCrypto.toHex(PassphraseWrap.wrap(pin, key)))
        editor.putString(wrap(KEY_WRAP_RECOVERY), AppLockCrypto.toHex(PassphraseWrap.wrap(recoverySecret, key)))
    }

    private fun rewrap(prefsKey: String, secret: String, extra: SharedPreferences.Editor.() -> Unit = {}): Boolean {
        val key = session ?: return false
        rememberKey(prefsKey)
        val editor = prefs.edit()
        applyRewrap(editor, prefsKey, secret, key)
        editor.extra()
        return editor.commitObserved()
    }

    private fun applyRewrap(editor: SharedPreferences.Editor, prefsKey: String, secret: String, key: ByteArray) {
        editor.putString(wrap(prefsKey), AppLockCrypto.toHex(PassphraseWrap.wrap(secret, key)))
    }

    private fun pinMatchesWrap(secret: String, wrapped: ByteArray): Boolean {
        val unwrapped = PassphraseWrap.unwrap(secret, wrapped) ?: return false
        unwrapped.fill(0)
        return true
    }

    private fun rememberWrapState() {
        val hex = prefs.getString(wrap(KEY_PASSPHRASE), null)
        val pinWrap = prefs.getString(wrap(KEY_WRAP_PIN), null)
        val recoveryWrap = prefs.getString(wrap(KEY_WRAP_RECOVERY), null)
        val biometricWrap = prefs.getString(wrap(KEY_WRAP_BIOMETRIC), null)
        lastWrapUndo = {
            restore(wrap(KEY_PASSPHRASE), hex)
            restore(wrap(KEY_WRAP_PIN), pinWrap)
            restore(wrap(KEY_WRAP_RECOVERY), recoveryWrap)
            restore(wrap(KEY_WRAP_BIOMETRIC), biometricWrap)
        }
    }

    private fun rememberKey(prefsKey: String) {
        val scoped = wrap(prefsKey)
        val previous = prefs.getString(scoped, null)
        lastWrapUndo = { restore(scoped, previous) }
    }

    private fun wrap(name: String): String = VaultKeys.of(selection.id, name)

    private fun text(name: String): String? = prefs.getString(wrap(name), null)?.takeIf { it.isNotBlank() }

    private fun decimal(name: String, fallback: BigDecimal): BigDecimal =
        text(name)?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: fallback

    private fun pair(firstName: String, first: String, secondName: String, second: String): Boolean = prefs.edit()
        .putIfPresent(firstName, first)
        .putIfPresent(secondName, second)
        .commitObserved()

    private fun SharedPreferences.Editor.putIfPresent(name: String, value: String): SharedPreferences.Editor {
        if (value.isNotBlank()) putString(wrap(name), value.trim())
        return this
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
        private const val KEY_T212_SYNC = "trading212_sync"
        private const val KEY_T212_KEY = "trading212_key"
        private const val KEY_T212_SECRET = "trading212_secret"
        private const val KEY_T212_STATUS = "trading212_status"
        private const val KEY_FIRE_EXPENSES = "fire_expenses"
        private const val KEY_FIRE_WITHDRAWAL = "fire_withdrawal"
        private const val KEY_FIRE_SAVINGS = "fire_savings"
        private const val KEY_FIRE_RETURN = "fire_return"
        private const val KEY_IBKR_TOKEN = "ibkr_token"
        private const val KEY_IBKR_QUERY = "ibkr_query"
        private const val KEY_KRAKEN_KEY = "kraken_key"
        private const val KEY_KRAKEN_SECRET = "kraken_secret"
        private const val KEY_COINBASE_NAME = "coinbase_key_name"
        private const val KEY_COINBASE_KEY = "coinbase_key"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
        private const val KEY_WEBDAV_PASSWORD = "webdav_password"
        private const val KEY_WEBDAV_ETAG = "webdav_etag"
        private const val KEY_OPENBB_URL = "openbb_base_url"
        private const val KEY_OPENBB_TOKEN = "openbb_token"
        private const val KEY_BROKER_STATUS = "broker_sync_status"
        private const val PASSPHRASE_BYTES: Int = 32
    }
}
