package com.antimaling.permanen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antimaling.permanen.AntiMalApp
import com.antimaling.permanen.net.MqttLink
import com.antimaling.permanen.spy.CamSnap
import com.antimaling.permanen.util.Prefs

/**
 * Service khusus pengambilan foto.
 *
 * Kenapa harus service sendiri (bukan langsung dari GuardService):
 * Android 14/15 MEWARIBAKAN foreground service bertipe "camera" — tipe itu hanya
 * sah kalau app sedang di foreground dan benar-benar memakai kamera. Kalau
 * dicampur ke GuardService (yang type-nya specialUse), seluruh service akan
 * gagal start dan ikut mati. Jadi pisahkan.
 *
 * Sengaja hanya boleh dipanggil saat ada Activity yang terlihat (LockActivity /
 * full-screen intent), sesuai aturan kamera Android.
 */
class CamService : Service() {

    private val CH = "cam"

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        makeChannel()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val front = i?.getBooleanExtra(EXTRA_FRONT, true) ?: true
        val reason = i?.getStringExtra(EXTRA_REASON) ?: "permintaan dari panel"
        val alsoRing = i?.getBooleanExtra(EXTRA_RING, false) ?: false

        // masuk foreground DULUAN — Android mewajibkan < 5 detik, kalau tidak
        // service dibunuh dengan ForegroundServiceDidNotStartInTimeException
        val ok = try {
            ServiceCompat.startForeground(this, NOTIF_ID, notif("Mengambil foto…"), typeCamera())
            true
        } catch (e: Exception) {
            // jangan ditelan diam-diam: catat supaya kelihatan di panel kesehatan
            lastError = "startForeground gagal: ${e.message}"
            Prefs.setCamError(this, lastError)
            Log.e(TAG, lastError)
            false
        }

        val worker = Thread {
            val r = try { CamSnap.shoot(this, front) } catch (e: Exception) {
                CamSnap.SnapResult("", e.message ?: "error")
            }
            if (r.image.isNotEmpty()) {
                try { MqttLink.push("photo", "📷 ${if (front) "kamera depan" else "kamera belakang"} ($reason)", image = r.image) } catch (_: Exception) {}
                try { Prefs.setCamError(this, "OK") } catch (_: Exception) {}
            } else {
                try { MqttLink.push("photo", "❌ Foto gagal: ${r.err}") } catch (_: Exception) {}
                try { Prefs.setCamError(this, r.err) } catch (_: Exception) {}
            }
            if (alsoRing) {
                try { com.antimaling.permanen.control.RingManager.start(this) } catch (_: Exception) {}
            }
            try { Prefs.setLastPhoto(this, System.currentTimeMillis().toString()) } catch (_: Exception) {}
            try { stopSelf() } catch (_: Exception) {}
        }
        worker.isDaemon = true
        worker.name = "cam-svc"
        worker.start()

        // kalau startForeground gagal, jangan terus hidup tanpa status foreground
        if (!ok) {
            try { stopSelf() } catch (_: Exception) {}
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        try { super.onDestroy() } catch (_: Exception) {}
    }

    private fun typeCamera(): Int = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0

    private fun makeChannel() {
        try {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CH, "Ambil Foto", NotificationManager.IMPORTANCE_LOW)
            )
        } catch (_: Exception) {}
    }

    private fun notif(text: String): Notification =
        NotificationCompat.Builder(this, CH)
            .setContentTitle("AntiMaling")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "CamService"
        private const val NOTIF_ID = 104
        const val EXTRA_FRONT = "front"
        const val EXTRA_RING = "ring"
        const val EXTRA_REASON = "reason"

        @Volatile var running = false
        @Volatile var lastError: String = ""

        /**
         * Ambil satu foto lalu service mati sendiri.
         * WAJIB dipanggil dari konteks Activity yang terlihat.
         */
        fun snap(c: Context, front: Boolean, reason: String, alsoRing: Boolean = false) {
            try {
                val i = Intent(c, CamService::class.java).apply {
                    putExtra(EXTRA_FRONT, front)
                    putExtra(EXTRA_REASON, reason)
                    putExtra(EXTRA_RING, alsoRing)
                }
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                lastError = "gagal start: ${e.message}"
            }
        }
    }
}
