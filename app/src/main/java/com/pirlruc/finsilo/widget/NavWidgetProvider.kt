package com.pirlruc.finsilo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pirlruc.finsilo.MainActivity
import com.pirlruc.finsilo.R
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import java.text.NumberFormat
import java.util.Locale

/**
 * Glanceable EUR NAV from persisted `nav_history` (via [WidgetNavCache]).
 * Tapping opens [MainActivity], which is the lock screen when locked.
 */
class NavWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val point = WidgetNavCache(context).read()
        val views = RemoteViews(context.packageName, R.layout.widget_nav)
        if (point == null) {
            views.setTextViewText(R.id.widget_nav_value, context.getString(R.string.widget_nav_empty))
            views.setTextViewText(R.id.widget_nav_date, "")
        } else {
            views.setTextViewText(R.id.widget_nav_value, eur.format(point.valueEur))
            views.setTextViewText(R.id.widget_nav_date, point.date.toString())
        }
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    companion object {
        private val eur: NumberFormat =
            NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("PT").build())

        /** Refresh every instance from the cached last NAV point. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NavWidgetProvider::class.java))
            if (ids.isEmpty()) return
            NavWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}
