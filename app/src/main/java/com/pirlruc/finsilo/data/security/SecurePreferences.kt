package com.pirlruc.finsilo.data.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Keystore AES-256-GCM encrypted [SharedPreferences] for SQLCipher wraps, the app lock, and
 * widget NAV.
 *
 * Values are sealed with [AndroidPrefsKeystore.ALIAS]. There is no EncryptedSharedPreferences
 * install to migrate; this app has not shipped.
 */
internal object SecurePreferences {
    fun open(context: Context, fileName: String): SharedPreferences = open(context, fileName, AndroidKeystoreAesGcmAead())

    internal fun open(context: Context, fileName: String, aead: PrefsAead): SharedPreferences =
        KeystoreAesGcmPreferences(context.getSharedPreferences(fileName, Context.MODE_PRIVATE), aead)
}
