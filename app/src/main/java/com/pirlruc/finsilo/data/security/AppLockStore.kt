package com.pirlruc.finsilo.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pirlruc.finsilo.domain.lock.AppLockCrypto

/** Encrypted PIN, recovery hash, and biometric flag for the app lock. */
class AppLockStore(context: Context) {
    private val masterKey =
        MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            .build()

    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun isSetup(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun setup(pin: String, recoveryCode: String, biometric: Boolean): Boolean {
        if (!AppLockCrypto.pinOk(pin)) return false
        val pinSalt = AppLockCrypto.generateSalt()
        val recoverySalt = AppLockCrypto.generateSalt()
        val recovery = AppLockCrypto.normalizeRecovery(recoveryCode)
        prefs.edit()
            .putString(KEY_PIN_SALT, AppLockCrypto.toHex(pinSalt))
            .putString(KEY_PIN_HASH, AppLockCrypto.toHex(AppLockCrypto.hashSecret(pin, pinSalt)))
            .putString(KEY_RECOVERY_SALT, AppLockCrypto.toHex(recoverySalt))
            .putString(KEY_RECOVERY_HASH, AppLockCrypto.toHex(AppLockCrypto.hashSecret(recovery, recoverySalt)))
            .putBoolean(KEY_BIOMETRIC, biometric)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean = verifyStored(pin, KEY_PIN_SALT, KEY_PIN_HASH)

    fun verifyRecovery(code: String): Boolean = verifyStored(AppLockCrypto.normalizeRecovery(code), KEY_RECOVERY_SALT, KEY_RECOVERY_HASH)

    fun resetPin(newPin: String): Boolean {
        if (!AppLockCrypto.pinOk(newPin)) return false
        val salt = AppLockCrypto.generateSalt()
        prefs.edit()
            .putString(KEY_PIN_SALT, AppLockCrypto.toHex(salt))
            .putString(KEY_PIN_HASH, AppLockCrypto.toHex(AppLockCrypto.hashSecret(newPin, salt)))
            .apply()
        return true
    }

    fun rotateRecovery(newCode: String): Boolean {
        val recovery = AppLockCrypto.normalizeRecovery(newCode)
        if (recovery.length < 16) return false
        val salt = AppLockCrypto.generateSalt()
        prefs.edit()
            .putString(KEY_RECOVERY_SALT, AppLockCrypto.toHex(salt))
            .putString(KEY_RECOVERY_HASH, AppLockCrypto.toHex(AppLockCrypto.hashSecret(recovery, salt)))
            .apply()
        return true
    }

    private fun verifyStored(secret: String, saltKey: String, hashKey: String): Boolean {
        val salt = AppLockCrypto.fromHex(prefs.getString(saltKey, null).orEmpty()) ?: return false
        val expected = AppLockCrypto.fromHex(prefs.getString(hashKey, null).orEmpty()) ?: return false
        return AppLockCrypto.verify(secret, salt, expected)
    }

    companion object {
        private const val PREFS_FILE = "finsilo_lock"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_BIOMETRIC = "biometric"
    }
}
