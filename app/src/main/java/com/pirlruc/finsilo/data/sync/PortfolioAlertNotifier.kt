package com.pirlruc.finsilo.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pirlruc.finsilo.R
import com.pirlruc.finsilo.domain.usecase.AlertChannel
import com.pirlruc.finsilo.domain.usecase.PortfolioAlert

class PortfolioAlertNotifier(private val context: Context) {
    fun publish(alerts: List<PortfolioAlert>) {
        if (alerts.isEmpty()) return
        if (!canNotify()) return
        val unseen = alerts.filterNot { alreadyPublished(it) }
        if (unseen.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        unseen.forEach { alert ->
            val notification =
                Notification.Builder(context, channelId(alert.channel))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(alert.title)
                    .setContentText(alert.body)
                    .setAutoCancel(true)
                    .build()
            manager.notify(notificationId(alert), notification)
            remember(alert)
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(channel(CHANNEL_RATING, "Rating changes"))
        manager.createNotificationChannel(channel(CHANNEL_CROSS, "SMA crosses"))
        manager.createNotificationChannel(channel(CHANNEL_DRIFT, "Allocation drift"))
    }

    private fun channel(id: String, name: String): NotificationChannel =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)

    private fun channelId(channel: AlertChannel): String = when (channel) {
        AlertChannel.RATING -> CHANNEL_RATING
        AlertChannel.CROSS -> CHANNEL_CROSS
        AlertChannel.DRIFT -> CHANNEL_DRIFT
    }

    private fun alreadyPublished(alert: PortfolioAlert): Boolean =
        prefs().getStringSet(KEY_PUBLISHED, emptySet()).orEmpty().contains(alertKey(alert))

    private fun remember(alert: PortfolioAlert) {
        val next = prefs().getStringSet(KEY_PUBLISHED, emptySet()).orEmpty().toMutableSet()
        next += alertKey(alert)
        prefs().edit().putStringSet(KEY_PUBLISHED, next).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alertKey(alert: PortfolioAlert): String = "${alert.channel}|${alert.title}|${alert.body}"

    private fun notificationId(alert: PortfolioAlert): Int = NOTIFICATION_BASE + (alertKey(alert).hashCode() and 0x7fffffff) % ID_SPAN

    companion object {
        private const val CHANNEL_RATING = "finsilo-rating"
        private const val CHANNEL_CROSS = "finsilo-cross"
        private const val CHANNEL_DRIFT = "finsilo-drift"
        private const val NOTIFICATION_BASE = 4100
        private const val ID_SPAN = 100_000
        private const val PREFS = "finsilo-alerts"
        private const val KEY_PUBLISHED = "published"
    }
}
