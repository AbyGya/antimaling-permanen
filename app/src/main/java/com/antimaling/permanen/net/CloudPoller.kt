package com.antimaling.permanen.net

import android.content.Context
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.util.Prefs

/**
 * Polling cloud tiap 10 dtk: heartbeat + ambil perintah + eksekusi + kirim hasil.
 * Jalan di thread daemon milik GuardService. Semua aman try-catch (anti-FC).
 */
object CloudPoller {
    @Volatile private var running = false

    fun start(c: Context) {
        if (running) return
        running = true
        val app = c.applicationContext
        Thread {
            try { Thread.sleep(5000) } catch (_: Exception) {}
            while (running) {
                try { tick(app) } catch (_: Exception) {}
                try { Thread.sleep(10000) } catch (_: Exception) { break }
            }
        }.apply { isDaemon = true; name = "cloud-poll"; start() }
    }

    fun tickOnce(c: Context): String {
        return try {
            if (!Prefs.cloudOn(c)) return "Cloud belum diset (isi API key + project + kode pairing)."
            if (!FirebaseRest.ensureAuth(c)) return "Gagal auth Firebase (cek API key)."
            val aid = Prefs.getAndroidId(c)
            FirebaseRest.heartbeat(c, aid)
            val cmds = FirebaseRest.listInbox(c, aid)
            if (cmds.isEmpty()) return "Online ✅ tidak ada perintah baru."
            var n = 0
            cmds.forEach { cmd ->
                try {
                    val r = CommandHandler.execCloud(c, cmd.type, cmd.arg)
                    FirebaseRest.sendResult(c, aid, if (r.image.isNotEmpty()) "image" else "text", cmd.type, r.text, r.image)
                    FirebaseRest.deleteDoc(c, cmd.name)
                    n++
                } catch (_: Exception) {}
            }
            "Online ✅ $n perintah dieksekusi."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun tick(c: Context) {
        try {
            if (!Prefs.cloudOn(c)) return
            if (!FirebaseRest.ensureAuth(c)) return
            val aid = Prefs.getAndroidId(c)
            FirebaseRest.heartbeat(c, aid)
            FirebaseRest.listInbox(c, aid).forEach { cmd ->
                try {
                    val r = CommandHandler.execCloud(c, cmd.type, cmd.arg)
                    FirebaseRest.sendResult(c, aid, if (r.image.isNotEmpty()) "image" else "text", cmd.type, r.text, r.image)
                    FirebaseRest.deleteDoc(c, cmd.name)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
