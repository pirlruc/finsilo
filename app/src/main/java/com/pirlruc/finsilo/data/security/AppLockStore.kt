package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.lock.PinLockoutPolicy

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

    fun recordFailedUnlock(nowMs: Long)

    fun clearUnlockFailures()
}

/** Encrypted PIN, recovery hash, and biometric flag for the app lock. */
class AppLockStore(private val prefs: SharedPreferences) : AppLockRepository {
    override fun isSetup(): Boolean = prefs.contains(KEY_PIN_HASH)

    override fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    override fun setBiometricEnabled(enabled: Boolean): Boolean = prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).commit()

    override fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean {
        if (!AppLockCrypto.pinOk(pin)) return false
        val editor = prefs.edit()
        applySetup(editor, pin, recoveryCode, biometric)
        return editor.commit()
    }

    override fun verifyPin(pin: String): Boolean = verifyStored(pin, KEY_PIN_SALT, KEY_PIN_HASH)

    override fun verifyRecovery(code: String): Boolean =
        verifyStored(AppLockCrypto.normalizeRecovery(code), KEY_RECOVERY_SALT, KEY_RECOVERY_HASH)

    override fun resetPin(newPin: String): Boolean {
        if (!AppLockCrypto.pinOk(newPin)) return false
        val editor = prefs.edit()
        applyPinReset(editor, newPin)
        return editor.commit()
    }

    override fun rotateRecovery(newCode: String): Boolean {
        val recovery = AppLockCrypto.normalizeRecovery(newCode)
        if (recovery.length < 16) return false
        val editor = prefs.edit()
        applyRecoveryHash(editor, newCode)
        return editor.commit()
    }

    override fun failedUnlockAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    override fun pinLockoutUntilMs(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    override fun recordFailedUnlock(nowMs: Long) {
        val attempts = failedUnlockAttempts() + 1
        val until = nowMs + PinLockoutPolicy.lockoutMs(attempts)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKOUT_UNTIL, until)
            .commit()
    }

    override fun clearUnlockFailures() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKOUT_UNTIL).commit()
    }

    internal fun applySetup(editor: SharedPreferences.Editor, pin: String, recoveryCode: String, biometric: Boolean) {
        applyHashedSecret(editor, KEY_PIN_SALT, KEY_PIN_HASH, pin)
        applyHashedSecret(editor, KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, AppLockCrypto.normalizeRecovery(recoveryCode))
        editor.putBoolean(KEY_BIOMETRIC, biometric)
        editor.remove(KEY_FAILED_ATTEMPTS)
        editor.remove(KEY_LOCKOUT_UNTIL)
    }

    internal fun applyPinReset(editor: SharedPreferences.Editor, newPin: String) {
        applyHashedSecret(editor, KEY_PIN_SALT, KEY_PIN_HASH, newPin)
        editor.remove(KEY_FAILED_ATTEMPTS)
        editor.remove(KEY_LOCKOUT_UNTIL)
    }

    internal fun applyRecoveryHash(editor: SharedPreferences.Editor, newCode: String) {
        applyHashedSecret(editor, KEY_RECOVERY_SALT, KEY_RECOVERY_HASH, AppLockCrypto.normalizeRecovery(newCode))
    }

    private fun applyHashedSecret(editor: SharedPreferences.Editor, saltKey: String, hashKey: String, secret: String) {
        val salt = AppLockCrypto.generateSalt()
        editor.putString(saltKey, AppLockCrypto.toHex(salt))
        editor.putString(hashKey, AppLockCrypto.toHex(AppLockCrypto.hashSecret(secret, salt)))
    }

    private fun verifyStored(secret: String, saltKey: String, hashKey: String): Boolean {
        val salt = AppLockCrypto.fromHex(prefs.getString(saltKey, null).orEmpty()) ?: return false
        val expected = AppLockCrypto.fromHex(prefs.getString(hashKey, null).orEmpty()) ?: return false
        return AppLockCrypto.verify(secret, salt, expected)
    }

    companion object {
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_ms"
    }
}
