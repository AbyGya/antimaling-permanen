package com.antimaling.permanen.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antimaling.permanen.AntiMalApp
import com.antimaling.permanen.R
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.control.FlashManager
import com.antimaling.permanen.control.LocateManager
import com.antimaling.permanen.control.RingManager
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.net.MqttLink
import com.antimaling.permanen.ui.MainActivity
import com.antimaling.permanen.util.Prefs
import java.util.concurrent.TimeUnit

class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    // Watchdog: selama terkunci, pastikan banner overlay hidup.
    // (Start service dari service foreground yang sudah jalan = diizinkan.)
    private val watch = object : Runnable {
        override fun run() {
            try {
                if (Prefs.isLocked(this@GuardService) && !OverlayService.running) {
                    OverlayService.restart(this@GuardService)
                }
            } catch (_: Exception) {}
            // jaga link laptop tetap nyambung
            try { MqttLink.start(this@GuardService) } catch (_: Exception) {}
            try { handler.postDelayed(this, 15000) } catch (_: Exception) {}
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try { startForeground(101, notif()) } catch (_: Exception) {}
        try { MqttLink.start(this) } catch (_: Exception) {}
        try { handler.postDelayed(watch, 15000) } catch (_: Exception) {}
        try {
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("keep", ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        try { startForeground(101, notif()) } catch (_: Exception) {}
        try { handleAction(i?.action) } catch (_: Exception) {}
        // jika terkunci tapi overlay mati (mis. dibunuh), hidupkan lagi
        try {
            if (Prefs.isLocked(this) && !CommandHandler.canDrawOverlay(this)) {
                val li = Intent(this, LockActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(li) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return START_STICKY
    }

    private fun handleAction(a: String?) {
        when (a) {
            "LOCK" -> CommandHandler.lock(this)
            "UNLOCK" -> CommandHandler.unlock(this)
            "RING" -> RingManager.start(this)
            "STOP" -> { RingManager.stop(this); FlashManager.stop(this) }
            "LOCATE" -> LocateManager.requestAndReply(this, Prefs.getTrusted(this).ifBlank { null })
        }
    }

    private fun notif(): Notification {
        val pi = try {
            val it = Intent(this, MainActivity::class.java)
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } catch (_: Exception) { null }
        return NotificationCompat.Builder(this, AntiMalApp.CH_GUARD)
            .setContentTitle("AntiMaling aktif")
            .setContentText("Proteksi permanen berjalan")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(root: Intent?) {
        // anti-kill: minta restart
        try {
            val it = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it)
        } catch (_: Exception) {}
        super.onTaskRemoved(root)
    }

    override fun onDestroy() {
        try { handler.removeCallbacks(watch) } catch (_: Exception) {}
        // self-heal kecuali user STOP eksplisit
        try {
            if (Prefs.isLocked(this) || Prefs.isRinging(this)) start(this)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        fun start(c: Context) {
            try {
                val it = Intent(c, GuardService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it) else c.startService(it)
            } catch (_: Exception) {}
        }
    }
}
