package com.pirlruc.finsilo.data.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Keystore AES-256-GCM encrypted [SharedPreferences] for SQLCipher wraps, the app lock, and
 * widget NAV.
 *
 * Values are sealed with [AndroidPrefsKeystore.ALIAS], not the deprecated EncryptedSharedPreferences
 * / MasterKey APIs. Existing Tink files (`fileName`) are copied once into [`storageName`] and deleted.
 */
internal object SecurePreferences {
    internal const val FORMAT_MARKER = "_finsilo_aes_gcm"
    internal const val FORMAT_VALUE = "1"

    fun open(context: Context, fileName: String): SharedPreferences = open(context, fileName, AndroidKeystoreAesGcmAead())

    internal fun open(context: Context, fileName: String, aead: PrefsAead): SharedPreferences {
        val dest = context.getSharedPreferences(storageName(fileName), Context.MODE_PRIVATE)
        EncryptedSharedPreferencesMigrator.ensureMigrated(context, fileName, dest, aead)
        return KeystoreAesGcmPreferences(dest, aead)
    }

    internal fun storageName(fileName: String): String = "${fileName}_ks"
}
