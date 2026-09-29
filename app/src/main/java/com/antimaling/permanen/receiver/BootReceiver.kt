package com.antimaling.permanen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antimaling.permanen.service.GuardService
import com.antimaling.permanen.service.KeepAlive
import com.antimaling.permanen.service.OverlayService
import com.antimaling.permanen.util.Prefs

/**
 * Receiver untuk boot & alarm penghidup ulang.
 *
 * Android 16 (XOS 16 = Android 16) memperketat beberapa hal:
 *  - BOOT_COMPLETED tidak lagi boleh memulai foreground service bertipe
 *    "while-in-use" (location/camera/mic). Karena GuardService memakai
 *    "specialUse" (bukan while-in-use), path ini tetap diizinkan.
 *  - App yang memegang izin SYSTEM_ALERT_WINDOW hanya boleh memulai
 *    foreground service dari background JIKA sedang ada overlay yang terlihat.
 *    Makanya startOverlay di sini selalu di-try/catch dan dicatat, dan
 *    diulang lagi oleh watchdog GuardService.
 *
 * Prinsipnya: apa pun yang gagal, tetaplah dipasang semua jaring
 * pengaman supaya ada percobaan lagi nanti.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent?) {
        try {
            val a = i?.action ?: ""
            val isAlarm = a == GuardService.RESTART_ACTION
            val isBoot = a.isEmpty() ||
                a.contains("BOOT_COMPLETED") || a.contains("QUICKBOOT") ||
                a.contains("MY_PACKAGE_REPLACED") || a.contains("PACKAGE_REPLACED")
            if (!isAlarm && !isBoot) return

            // 1. jaring pengaman yang tidak butuh foreground service.
            //    Selalu dipasang DULUAN — kalau startForegroundService kena
            //    ForegroundServiceStartNotAllowedException, jaring ini yang
            //    masih bisa menyelamatkan aplikasi.
            try { KeepAlive.scheduleJob(c) } catch (_: Exception) {}
            try { KeepAlive.scheduleWorker(c) } catch (_: Exception) {}

            // 2. hidupkan guard
            try {
                GuardService.start(c)
            } catch (e: Exception) {
                // GuardService.start() sudah mencatat sendiri ke panel kesehatan
                Log.w("BootReceiver", "start guard ditolak: ${e.message}")
            }

            // 3. overlay — hanya kalau dibutuhkan, dan gagalnya tidak fatal
            try {
                if (Prefs.isOverlay(c) || Prefs.isLocked(c)) OverlayService.restart(c)
            } catch (_: Exception) {}

            // 4. konektivitas panel
            try { com.antimaling.permanen.net.MqttLink.start(c) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
