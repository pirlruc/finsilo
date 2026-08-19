package com.pirlruc.finsilo.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * One-shot copy from deprecated EncryptedSharedPreferences into [KeystoreAesGcmPreferences].
 *
 * Keep [androidx.security.crypto] on the classpath until existing installs have moved off Tink
 * files. Do not open the legacy file as plaintext SharedPreferences — that would look like a wipe.
 */
internal object EncryptedSharedPreferencesMigrator {
    fun ensureMigrated(
        context: Context,
        sourceFileName: String,
        dest: SharedPreferences,
        aead: PrefsAead,
    ) {
        if (dest.contains(SecurePreferences.FORMAT_MARKER)) {
            deleteLegacyXml(context, sourceFileName)
            return
        }
        writeSnapshot(dest, aead, readLegacySnapshot(context, sourceFileName))
        dest.edit()
            .putString(SecurePreferences.FORMAT_MARKER, SecurePreferences.FORMAT_VALUE)
            .commit()
        deleteLegacyXml(context, sourceFileName)
    }

    @Suppress("DEPRECATION")
    internal fun openLegacyEsp(context: Context, fileName: String): SharedPreferences {
        val masterKey =
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
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun readLegacySnapshot(context: Context, sourceFileName: String): Map<String, *> {
        if (!prefsXml(context, sourceFileName).exists()) return emptyMap()
        return HashMap(openLegacyEsp(context, sourceFileName).all)
    }

    private fun writeSnapshot(dest: SharedPreferences, aead: PrefsAead, snapshot: Map<String, *>) {
        val editor = KeystoreAesGcmPreferences(dest, aead).edit()
        for ((key, value) in snapshot) {
            putLegacyValue(editor, key, value)
        }
        check(editor.commit()) { "Failed to write migrated secure preferences" }
    }

    private fun putLegacyValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toMutableSet())
            else -> editor.putString(key, value.toString())
        }
    }

    internal fun deleteLegacyXml(context: Context, name: String) {
        val xml = prefsXml(context, name)
        xml.delete()
        File("${xml.path}.bak").delete()
    }

    internal fun prefsXml(context: Context, name: String): File =
        File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
}
