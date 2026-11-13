package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy
import com.pirlruc.finsilo.domain.vault.VaultKeys
import com.pirlruc.finsilo.domain.vault.VaultSelection

/** PIN, recovery, biometric flag, and unlock-attempt lockout. */
interface AppLockRepository {
    fun isSetup(): Boolean

    fun biometricEnabled(): Boolean

    fun setBiometricEnabled(enabled: Boolean): Boolean

    fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean

    fun verifyPin(pin: String): Boolean

    fun verifyRecovery(code: String): Boolean

    fun resetPin(newPin: String): Boolean

    fun rotateRecovery(newCode: String): Boolean

    fun failedUnlockAttempts(): Int

    fun pinLockoutUntilMs(): Long

    fun lockoutElapsedUntilMs(): Long = 0L

    fun lockoutBootCount(): Int = -1

    fun lockoutRemaining(nowMs: Long): Long = com.pirlruc.finsilo.domain.lock.PinLockoutPolicy.remainingMs(nowMs, pinLockoutUntilMs())

    fun recordFailedUnlock(nowMs: Long)

    fun clearUnlockFailures()
}

/** Encrypted PIN, recovery hash, and biometric flag for the app lock. */
class AppLockStore(
    private val prefs: SharedPreferences,
    private val selection: VaultSelection = VaultSelection(),
    private val clock: () -> LockoutSample = { LockoutSample(System.currentTimeMillis(), System.currentTimeMillis(), -1) },
) : AppLockRepository {
    override fun isSetup(): Boolean = prefs.contains(key(KEY_PIN_HASH))

    override fun biometricEnabled(): Boolean = prefs.getBoolean(key(KEY_BIOMETRIC), false)

    override fun setBiometricEnabled(enabled: Boolean): Boolean = prefs.edit().putBoolean(key(KEY_BIOMETRIC), enabled).commitObserved()

    override fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean {
        if (!AppLockCrypto.pinOk(pin)) return false
        val editor = prefs.edit()
        applySetup(editor, pin, recoveryCode, biometric)
        return editor.commitObserved()
    }

    override fun verifyPin(pin: String): Boolean = verifyStored(pin, KEY_PIN_SALT, KEY_PIN_HASH)

    override fun verifyRecovery(code: String): Boolean =
        verifyStored(AppLockCrypto.normalizeRecovery(code), KEY_RECOVERY_SALT, KEY_RECOVERY_HASH)

    override fun resetPin(newPin: String): Boolean {
        if (!AppLockCrypto.pinOk(newPin)) return false
        val editor = prefs.edit()
        applyPinReset(editor, newPin)
        return editor.commitObserved()
    }

    override fun rotateRecovery(newCode: String): Boolean {
        val recovery = AppLockCrypto.normalizeRecovery(newCode)
        if (recovery.length < 16) return false
        val editor = prefs.edit()
        applyRecoveryHash(editor, newCode)
        return editor.commitObserved()
    }

    override fun failedUnlockAttempts(): Int = prefs.getInt(key(KEY_FAILED_ATTEMPTS), 0)

    override fun pinLockoutUntilMs(): Long = prefs.getLong(key(KEY_LOCKOUT_UNTIL), 0L)

    override fun lockoutElapsedUntilMs(): Long = prefs.getLong(key(KEY_LOCKOUT_ELAPSED), 0L)

    override fun lockoutBootCount(): Int = prefs.getInt(key(KEY_LOCKOUT_BOOT), -1)

    override fun lockoutRemaining(nowMs: Long): Long {
        val sample = clock()
        return PinLockoutPolicy.remainingMs(
            nowWallMs = nowMs,
            untilWallMs = pinLockoutUntilMs(),
            nowElapsedMs = sample.elapsedMs,
            untilElapsedMs = lockoutElapsedUntilMs(),
            nowBoot = sample.bootCount,
            lockBoot = lockoutBootCount(),
        )
    }

    override fun recordFailedUnlock(nowMs: Long) {
        val attempts = failedUnlockAttempts() + 1
        val wait = PinLockoutPolicy.lockoutMs(attempts)
        val sample = clock()
        prefs.edit()
            .putInt(key(KEY_FAILED_ATTEMPTS), attempts)
            .putLong(key(KEY_LOCKOUT_UNTIL), nowMs + wait)
            .putLong(key(KEY_LOCKOUT_ELAPSED), sample.elapsedMs + wait)
            .putInt(key(KEY_LOCKOUT_BOOT), sample.bootCount)
            .commitObserved()
    }

    override fun clearUnlockFailures() {
        prefs.edit()
            .remove(key(KEY_FAILED_ATTEMPTS))
            .remove(key(KEY_LOCKOUT_UNTIL))
            .remove(key(KEY_LOCKOUT_ELAPSED))
            .remove(key(KEY_LOCKOUT_BOOT))
            .commitObserved()
    }

    internal fun applySetup(editor: SharedPreferences.Editor, pin: String, recoveryCode: String, biometric: Boolean) {
        applyHashedSecret(editor, KEY_PIN_SALT, KEY_PIN_HASH, pin)
        applyHashedSecret(editor, KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, AppLockCrypto.normalizeRecovery(recoveryCode))
        editor.putBoolean(key(KEY_BIOMETRIC), biometric)
        editor.remove(key(KEY_FAILED_ATTEMPTS))
        editor.remove(key(KEY_LOCKOUT_UNTIL))
        editor.remove(key(KEY_LOCKOUT_ELAPSED))
        editor.remove(key(KEY_LOCKOUT_BOOT))
    }

    internal fun applyPinReset(editor: SharedPreferences.Editor, newPin: String) {
        applyHashedSecret(editor, KEY_PIN_SALT, KEY_PIN_HASH, newPin)
        editor.remove(key(KEY_FAILED_ATTEMPTS))
        editor.remove(key(KEY_LOCKOUT_UNTIL))
        editor.remove(key(KEY_LOCKOUT_ELAPSED))
        editor.remove(key(KEY_LOCKOUT_BOOT))
    }

    internal fun applyRecoveryHash(editor: SharedPreferences.Editor, newCode: String) {
        applyHashedSecret(editor, KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, AppLockCrypto.normalizeRecovery(newCode))
    }

    private fun applyHashedSecret(editor: SharedPreferences.Editor, saltKey: String, hashKey: String, secret: String) {
        val salt = AppLockCrypto.generateSalt()
        editor.putString(key(saltKey), AppLockCrypto.toHex(salt))
        editor.putString(key(hashKey), AppLockCrypto.toHex(AppLockCrypto.hashSecret(secret, salt)))
    }

    private fun verifyStored(secret: String, saltKey: String, hashKey: String): Boolean {
        val salt = AppLockCrypto.fromHex(prefs.getString(key(saltKey), null).orEmpty()) ?: return false
        val expected = AppLockCrypto.fromHex(prefs.getString(key(hashKey), null).orEmpty()) ?: return false
        return AppLockCrypto.verify(secret, salt, expected)
    }

    private fun key(name: String): String = VaultKeys.of(selection.id, name)

    companion object {
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_ms"
        private const val KEY_LOCKOUT_ELAPSED = "pin_lockout_elapsed_until"
        private const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
    }
}

/** Wall clock, elapsed realtime, and boot count captured together. */
data class LockoutSample(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)
