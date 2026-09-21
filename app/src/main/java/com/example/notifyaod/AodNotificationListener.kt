package com.example.notifyaod

import android.app.Notification
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AodNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        // Пропускаем «сводные» и постоянные уведомления, чтобы не было дублей и ложных срабатываний
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return

        val pkg = sbn.packageName
        scope.launch {
            val s = repo.settings.first()
            if (pkg !in s.selectedPackages) return@launch
            if (QuietHours.isQuiet(s)) return@launch

            // Показываем, только если экран выключен (или наш экран уже на виду — тогда просто продлеваем)
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isInteractive && !AodActivity.visible) return@launch

            withContext(Dispatchers.Main) {
                AodLauncher.show(applicationContext, listOf(pkg))
            }
        }
    }
}
