package com.pirlruc.finsilo.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Stores the SQLCipher passphrase in EncryptedSharedPreferences backed by the Android Keystore.
 * The raw passphrase never leaves the app's encrypted prefs.
 */
class DatabaseKeyStore(context: Context) {
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

    fun passphrase(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return existing.hexToBytes()
        }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, generated.toHex()).apply()
        return generated
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val PREFS_FILE = "finsilo_secure"
        private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
    }
}
