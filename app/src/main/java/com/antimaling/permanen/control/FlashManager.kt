package com.antimaling.permanen.control

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import com.antimaling.permanen.util.Perms

object FlashManager {
    @Volatile private var running = false
    @Volatile private var thread: Thread? = null
    @Volatile private var gen = 0

    fun start(c: Context, seconds: Int = 60) {
        try {
            if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
            if (!Perms.camera(c)) {
                // tanpa izin kamera tetap coba (beberapa ROM mengizinkan torch tanpa runtime perm) — anti no-fungsi
            }
            stop(c)
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

    fun stop(c: Context) {
        try {
            gen++ // bunuh SEMUA thread kedip dari start() manapun
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

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: Exception) {} }
}
