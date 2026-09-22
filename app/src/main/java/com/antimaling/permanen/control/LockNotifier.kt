package com.antimaling.permanen.control

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antimaling.permanen.AntiMalApp
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.util.Prefs

/**
 * Notifikasi alarm full-screen: SATU-SATUNYA cara resmi menampilkan
 * Activity dari background (receiver SMS/boot) di Android 10+.
 * startActivity/startForegroundService langsung dari background DIBLOKIR
 * sistem -> itulah kenapa overlay tidak muncul saat HP idle.
 */
object LockNotifier {
    const val ID = 103

    fun fire(c: Context) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java) ?: return
            try {
                if (Build.VERSION.SDK_INT >= 26 &&
                    nm.getNotificationChannel(AntiMalApp.CH_ALARM) == null
                ) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            AntiMalApp.CH_ALARM, "Alarm Darurat",
                            NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }
            } catch (_: Exception) {}
            val pi = try {
                PendingIntent.getActivity(
                    c, 0, Intent(c, LockActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } catch (_: Exception) { return }
            val n = NotificationCompat.Builder(c, AntiMalApp.CH_ALARM)
                .setContentTitle("🔒 Perangkat terkunci")
                .setContentText(Prefs.getText(c))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            try { nm.notify(ID, n) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun cancel(c: Context) {
        try { c.getSystemService(NotificationManager::class.java)?.cancel(ID) } catch (_: Exception) {}
    }
}
