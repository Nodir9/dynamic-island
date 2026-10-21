package dev.island.app

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    private val lastSeen = HashMap<String, Int>()

    override fun onCreate() {
        super.onCreate()
        IslandOverlay.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val cat = n.category
        if (cat == Notification.CATEGORY_CALL ||
            cat == Notification.CATEGORY_TRANSPORT ||
            cat == Notification.CATEGORY_PROGRESS ||
            cat == Notification.CATEGORY_SERVICE
        ) return

        // тихие уведомления (беззвучные) не показываем
        try {
            val r = Ranking()
            if (currentRanking.getRanking(sbn.key, r) &&
                r.importance < NotificationManager.IMPORTANCE_DEFAULT
            ) return
        } catch (_: Exception) {
        }

        val ex = n.extras
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
        if (title.isBlank() && text.isBlank()) return

        // повторные обновления с тем же текстом игнорируем
        val h = (title + "|" + text).hashCode()
        if (lastSeen[sbn.key] == h) return
        lastSeen[sbn.key] = h

        val pm = packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        val icon = try {
            pm.getApplicationIcon(sbn.packageName)
        } catch (_: Exception) {
            null
        }

        IslandOverlay.post(
            IslandItem(icon, if (title.isBlank()) label else title, text, n.contentIntent)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastSeen.remove(sbn.key)
    }
}
