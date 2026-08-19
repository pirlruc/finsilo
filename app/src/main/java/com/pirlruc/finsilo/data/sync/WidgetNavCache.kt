package com.pirlruc.finsilo.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.pirlruc.finsilo.data.security.SecurePreferences
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.widget.NavWidgetProvider
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Last stored EUR NAV for the home-screen widget. Never calls a market API.
 *
 * Values are written with Keystore AES-256-GCM ([SecurePreferences]).
 * If Keystore prefs cannot be opened, reads return null and writes are no-ops
 * so a disk dump never falls back to plaintext. The widget can still render
 * without the app PIN; the ledger itself is PIN-wrapped (FS-027-T2).
 */
class WidgetNavCache(context: Context, prefsOverride: SharedPreferences? = null) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? =
        if (prefsOverride != null) {
            migratePlaintext(appContext, prefsOverride)
            prefsOverride
        } else {
            openEncryptedOrNull(appContext)?.also { encrypted -> migratePlaintext(appContext, encrypted) }
        }

    fun write(point: NavPoint?) {
        val target = prefs ?: return
        if (point == null) {
            target.edit().clear().apply()
        } else {
            target.edit()
                .putString(KEY_DATE, point.date.toString())
                .putString(KEY_VALUE, point.valueEur.toPlainString())
                .apply()
        }
        NavWidgetProvider.refreshAll(appContext)
    }

    fun read(): NavPoint? {
        val target = prefs ?: return null
        return parsePoint(target.getString(KEY_DATE, null), target.getString(KEY_VALUE, null))
    }

    private fun parsePoint(date: String?, value: String?): NavPoint? {
        if (date == null || value == null) return null
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val parsedValue = runCatching { BigDecimal(value) }.getOrNull() ?: return null
        return NavPoint(parsedDate, parsedValue)
    }

    companion object {
        private const val PREFS_SECURE = "finsilo_widget_nav_secure"
        private const val PREFS_PLAIN = "finsilo_widget_nav"
        private const val KEY_DATE = "date"
        private const val KEY_VALUE = "value_eur"

        private fun openEncryptedOrNull(context: Context): SharedPreferences? =
            runCatching { SecurePreferences.open(context, PREFS_SECURE) }.getOrNull()

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
