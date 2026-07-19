package com.magic.pagetime

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationClearService : NotificationListenerService() {

    companion object {
        var instance: NotificationClearService? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // Clears only notifications from the given package (e.g. com.android.chrome).
    // If targetPackage is blank, falls back to clearing everything.
    fun clearNotifications(targetPackage: String) {
        try {
            if (targetPackage.isBlank()) {
                cancelAllNotifications()
                return
            }
            val active = activeNotifications ?: return
            for (sbn in active) {
                if (sbn.packageName == targetPackage) {
                    cancelNotification(sbn.key)
                }
            }
        } catch (e: Exception) { /* listener not bound, ignore */ }
    }
}
