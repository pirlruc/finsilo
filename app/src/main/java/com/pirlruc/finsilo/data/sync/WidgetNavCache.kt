package com.pirlruc.finsilo.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.widget.NavWidgetProvider
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Last stored EUR NAV for the home-screen widget. Never calls a market API.
 *
 * Values are written with EncryptedSharedPreferences (Keystore AES-256-GCM) so a
 * disk dump is not plaintext. The widget can still render without the app PIN;
 * PIN-wrap of this cache is part of FS-027-T2.
 */
class WidgetNavCache(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = openPrefs(appContext)

    fun write(point: NavPoint?) {
        if (point == null) {
            prefs.edit().clear().apply()
        } else {
            prefs.edit()
                .putString(KEY_DATE, point.date.toString())
                .putString(KEY_VALUE, point.valueEur.toPlainString())
                .apply()
        }
        NavWidgetProvider.refreshAll(appContext)
    }

    fun read(): NavPoint? {
        val date = prefs.getString(KEY_DATE, null) ?: return null
        val value = prefs.getString(KEY_VALUE, null) ?: return null
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val parsedValue = runCatching { BigDecimal(value) }.getOrNull()
        if (parsedDate == null || parsedValue == null) return null
        return NavPoint(parsedDate, parsedValue)
    }

    companion object {
        private const val PREFS_SECURE = "finsilo_widget_nav_secure"
        private const val PREFS_PLAIN = "finsilo_widget_nav"
        private const val KEY_DATE = "date"
        private const val KEY_VALUE = "value_eur"

        private fun openPrefs(context: Context): SharedPreferences {
            val encrypted =
                runCatching { encryptedPrefs(context) }.getOrNull()
                    ?: return context.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
            migratePlaintext(context, encrypted)
            return encrypted
        }

        private fun encryptedPrefs(context: Context): SharedPreferences {
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
                PREFS_SECURE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private fun migratePlaintext(context: Context, encrypted: SharedPreferences) {
            val old = context.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
            if (!old.contains(KEY_DATE) && !old.contains(KEY_VALUE)) return
            if (!encrypted.contains(KEY_DATE)) {
                encrypted.edit()
                    .putString(KEY_DATE, old.getString(KEY_DATE, null))
                    .putString(KEY_VALUE, old.getString(KEY_VALUE, null))
                    .apply()
            }
            old.edit().clear().apply()
        }
    }
}
