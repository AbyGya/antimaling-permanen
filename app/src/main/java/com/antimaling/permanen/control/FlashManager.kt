package com.antimaling.permanen.control

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import com.antimaling.permanen.util.Prefs

object FlashManager {
    @Volatile private var running = false
    @Volatile private var thread: Thread? = null
    @Volatile private var gen = 0

    fun start(c: Context, seconds: Int = 60) {
        try {
            if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
            // Batalkan stop yang tertunda dari proses sebelumnya.
            // Tanpa baris ini, start() pernah menyalakan flag stop lewat
            // silence() lalu checkStopFlag() akan mematikan senter lagi begitu
            // service tersambung ulang — itu sebabnya "fungsi只在 app terbuka".
            Prefs.setStopFlash(c, false)
            silence(c)
            running = true
            val g = ++gen
            val cm = c.getSystemService(CameraManager::class.java) ?: return
            val camId = try { cm.cameraIdList.firstOrNull() ?: return } catch (_: Exception) { return }
            val end = System.currentTimeMillis() + seconds * 1000L
            thread = Thread {
                try {
                    while (running && g == gen && System.currentTimeMillis() < end) {
                        try { cm.setTorchMode(camId, true) } catch (_: Exception) { break }
                        sleep(350)
                        try { cm.setTorchMode(camId, false) } catch (_: Exception) { break }
                        sleep(350)
                    }
                } catch (_: Exception) {}
                try { cm.setTorchMode(camId, false) } catch (_: Exception) {}
                running = false
            }.apply { isDaemon = true; start() }
        } catch (_: Exception) {}
    }

    /**
     * Matikan senter tanpa menyentuh flag stop.
     * Dipakai baik saat akan mulai lagi maupun saatUOrmati stop tertunda.
     */
    private fun silence(c: Context) {
        try {
            gen++
            running = false
            thread?.interrupt()
            thread = null
            try { Thread.sleep(50) } catch (_: Exception) {}
            try {
                val cm = c.getSystemService(CameraManager::class.java)
                val id = try { cm?.cameraIdList?.firstOrNull() } catch (_: Exception) { null }
                if (cm != null && id != null) cm.setTorchMode(id, false)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /** Stop dari perintah pengguna/sistem: tandai persisten lalu hentikan. */
    fun stop(c: Context) {
        try {
            Prefs.setStopFlash(c, true)
            silence(c)
        } catch (_: Exception) {}
    }

    /**
     * Hormati stop yang tertunda setelah proses dibunuh OEM.
     * Flag dibersihkan DULUAN, lalu dihentikan tanpa menyalakan flag lagi —
     * kalau tidak, flag tidak akan pernah bisa bersih.
     */
    fun checkStopFlag(c: Context) {
        if (!Prefs.isStopFlash(c)) return
        Prefs.setStopFlash(c, false)
        silence(c)
    }

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: Exception) {} }
}
