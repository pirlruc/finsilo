package com.pirlruc.finsilo.data.sync

import android.content.Context
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.widget.NavWidgetProvider
import java.math.BigDecimal
import java.time.LocalDate

/** Last stored EUR NAV for the home-screen widget. Never calls a market API. */
class WidgetNavCache(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        private const val PREFS = "finsilo_widget_nav"
        private const val KEY_DATE = "date"
        private const val KEY_VALUE = "value_eur"
    }
}
