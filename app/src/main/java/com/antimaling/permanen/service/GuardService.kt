package com.antimaling.permanen.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antimaling.permanen.AntiMalApp
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.control.FlashManager
import com.antimaling.permanen.control.LocateManager
import com.antimaling.permanen.control.RingManager
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.net.MqttLink
import com.antimaling.permanen.ui.MainActivity
import com.antimaling.permanen.util.Prefs

class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    // Watchdog: selama service hidup, jaga overlay + link laptop + alarm tetap armed.
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
            // alarm harus selalu ter-armed ulang, kalau tidak dan proses dibunuh
            // OEM tidak ada apa pun yang membangunkan app lagi
            try { armRestartAlarm() } catch (_: Exception) {}
            try { handler.postDelayed(this, 15000) } catch (_: Exception) {}
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    /**
     * Masuk foreground dengan tipe MINIMAL.
     *
     * Dulu panggil startForeground(id, notif) yang memakai SEMUA tipe yang
     * dideklarasikan di manifest (saat itu termasuk camera|mediaProjection).
     * Di Android 14/15 itu ditolak saat dipanggil dari background, dan
     * exception-nya ditelan — sehingga service tidak pernah sampai kondisi
     * foreground lalu dibunuh sistem dalam ~5 detik.
     *
     * Sekarang: tipe eksplisit + error dicatat di Prefs supaya terlihat di app.
     */
    private fun goForeground(): Boolean {
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, notif(), typeSpecialUse())
            Prefs.setFgStatus(this, "OK")
            true
        } catch (e: Exception) {
            val msg = e.javaClass.simpleName + ": " + (e.message ?: "?")
            Prefs.setFgStatus(this, "GAGAL — $msg")
            Log.e(TAG, "startForeground gagal: $msg")
            false
        }
    }

    private fun typeSpecialUse(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    override fun onCreate() {
        super.onCreate()
        Prefs.setSvcStart(this)
        goForeground()
        try { MqttLink.start(this) } catch (_: Exception) {}
        // cek flag stop persisten (survive process kill)
        try { RingManager.checkStopFlag(this) } catch (_: Exception) {}
        try { FlashManager.checkStopFlag(this) } catch (_: Exception) {}
        try { armRestartAlarm() } catch (_: Exception) {}
        try { KeepAlive.scheduleJob(this) } catch (_: Exception) {}
        try { handler.postDelayed(watch, 15000) } catch (_: Exception) {}
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        try { goForeground() } catch (_: Exception) {}
        try { handleAction(i?.action) } catch (_: Exception) {}
        // re-arm alarm + job setiap kali service dinyalakan. Sebelumnya hanya
        // di onTaskRemoved/onDestroy, yang TIDAK dipanggil saat proses dibunuh
        // OEM — jadi tidak ada apa pun yang membangunkan app lagi.
        try { armRestartAlarm() } catch (_: Exception) {}
        try { KeepAlive.scheduleJob(this) } catch (_: Exception) {}
        // jika terkunci tapi overlay mati (mis. dibunuh), hidupkan lagi
        try {
            if (Prefs.isLocked(this) && !CommandHandler.canDrawOverlay(this) && !LockActivity.isShowing()) {
                LockActivity.show(this)
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
        val txt = try {
            if (Prefs.isLocked(this)) "🔒 Terkunci • panel tersambung" else "Proteksi aktif • panel tersambung"
        } catch (_: Exception) { "Proteksi permanen berjalan" }
        return NotificationCompat.Builder(this, AntiMalApp.CH_GUARD)
            .setContentTitle("AntiMaling aktif")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(root: Intent?) {
        // anti-kill: minta restart + pasang alarm cadangan 60 dtk
        try {
            val it = Intent(this, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(it) else startService(it)
        } catch (_: Exception) {}
        try { armRestartAlarm() } catch (_: Exception) {}
        super.onTaskRemoved(root)
    }

    override fun onDestroy() {
        try { handler.removeCallbacks(watch) } catch (_: Exception) {}
        // self-heal kecuali user STOP eksplisit
        try {
            if (Prefs.isLocked(this) || Prefs.isRinging(this)) start(this)
        } catch (_: Exception) {}
        try { armRestartAlarm() } catch (_: Exception) {}
        super.onDestroy()
    }

    /**
     * Alarm inexact (tanpa izin khusus): bangunkan service bila dibunuh OEM.
     *
     * WAJIB dipanggil ulang secara berkala (dari watchdog + onStartCommand),
     * bukan hanya di onDestroy/onTaskRemoved. Kedua callback itu tidak
     * dipanggil ketika proses dibunuh paksa oleh sistem, sehingga sebelumnya
     * tidak ada alarm yang pernah bersiap setelah HP dibunuh OEM.
     */
    private fun armRestartAlarm() {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                this, 7,
                Intent(this, com.antimaling.permanen.receiver.BootReceiver::class.java)
                    .setAction(RESTART_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60_000, pi
            )
            Prefs.setAlarmArmed(this, true)
        } catch (_: Exception) {
            try { Prefs.setAlarmArmed(this, false) } catch (_: Exception) {}
        }
    }

    companion object {
        const val RESTART_ACTION = "com.antimaling.permanen.RESTART"
        private const val TAG = "GuardService"
        private const val NOTIF_ID = 101

        fun start(c: Context) {
            try {
                val it = Intent(c, GuardService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it) else c.startService(it)
            } catch (e: Exception) {
                // startForegroundService dari background bisa ditolak Android 14+.
                // Catat supaya kelihatan, jangan ditelan diam-diam.
                try { Prefs.setFgStatus(c, "GAGAL start: ${e.message}") } catch (_: Exception) {}
                Log.e(TAG, "gagal start service: ${e.message}")
            }
        }
    }
}
