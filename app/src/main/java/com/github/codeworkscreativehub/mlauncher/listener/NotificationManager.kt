package com.github.codeworkscreativehub.mlauncher.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.github.codeworkscreativehub.common.AppLogger

private const val TAG = "NotifCount"

class NotificationManager : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        AppLogger.d(
            TAG,
            "${sbn.packageName} Notification: ${sbn.notification.extras.getString(Notification.EXTRA_TITLE)} | " +
                    "${sbn.notification.extras.getString(Notification.EXTRA_TEXT)}"
        )
        updateDots()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateDots()
    }

    private fun updateDots() {
        // Get current active notifications
        val active = activeNotifications ?: return

        // Count notifications per package
        val counts = active.groupingBy { it.packageName }.eachCount()

        // Update NotificationDotManager
        counts.forEach { (pkg, count) ->
            NotificationDotManager.setCount(pkg, count)
            AppLogger.d(TAG, "$pkg has $count notifications")
        }

        // Remove packages that no longer have notifications
        val trackedPackages = NotificationDotManager.getAllCounts().keys
        trackedPackages.forEach { pkg ->
            if (!counts.containsKey(pkg)) {
                NotificationDotManager.removeCount(pkg)
                AppLogger.d(TAG, "$pkg removed from NotificationDotManager")
            }
        }
    }
}
