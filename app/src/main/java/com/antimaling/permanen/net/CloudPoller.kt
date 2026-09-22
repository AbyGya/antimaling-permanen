package com.antimaling.permanen.net

import android.content.Context
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.util.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun stamp(ok: Boolean, detail: String): String {
        return try {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            (if (ok) "✅ " else "❌ ") + t + " " + detail
        } catch (_: Exception) { detail }
    }

    fun tickOnce(c: Context): String {
        return try {
            if (!Prefs.cloudOn(c)) {
                val m = "Cloud belum diset (isi API key + project + kode pairing)."
                Prefs.setLastSync(c, stamp(false, m)); return m
            }
            if (!FirebaseRest.ensureAuth(c)) {
                val m = "Gagal auth Firebase (cek API key + Anonymous aktif)."
                Prefs.setLastSync(c, stamp(false, m)); return m
            }
            val aid = Prefs.getAndroidId(c)
            if (!FirebaseRest.heartbeat(c, aid)) {
                val m = "Gagal tulis database (cek rules Published + internet HP)."
                Prefs.setLastSync(c, stamp(false, m)); return m
            }
            val cmds = FirebaseRest.listInbox(c, aid)
            if (cmds.isEmpty()) {
                val m = "Online ✅ tidak ada perintah baru."
                Prefs.setLastSync(c, stamp(true, m)); return m
            }
            var n = 0
            cmds.forEach { cmd ->
                try {
                    val r = CommandHandler.execCloud(c, cmd.type, cmd.arg)
                    FirebaseRest.sendResult(c, aid, if (r.image.isNotEmpty()) "image" else "text", cmd.type, r.text, r.image)
                    FirebaseRest.deleteDoc(c, cmd.name)
                    n++
                } catch (_: Exception) {}
            }
            val m = "Online ✅ $n perintah dieksekusi."
            Prefs.setLastSync(c, stamp(true, m)); m
        } catch (e: Exception) {
            val m = "Error: ${e.message}"
            try { Prefs.setLastSync(c, stamp(false, m)) } catch (_: Exception) {}
            m
        }
    }

    private fun tick(c: Context) {
        try {
            if (!Prefs.cloudOn(c)) return
            if (!FirebaseRest.ensureAuth(c)) {
                try { Prefs.setLastSync(c, stamp(false, "auth gagal")) } catch (_: Exception) {}
                return
            }
            val aid = Prefs.getAndroidId(c)
            if (!FirebaseRest.heartbeat(c, aid)) {
                try { Prefs.setLastSync(c, stamp(false, "tulis DB gagal")) } catch (_: Exception) {}
                return
            }
            FirebaseRest.listInbox(c, aid).forEach { cmd ->
                try {
                    val r = CommandHandler.execCloud(c, cmd.type, cmd.arg)
                    FirebaseRest.sendResult(c, aid, if (r.image.isNotEmpty()) "image" else "text", cmd.type, r.text, r.image)
                    FirebaseRest.deleteDoc(c, cmd.name)
                } catch (_: Exception) {}
            }
            try { Prefs.setLastSync(c, stamp(true, "sync ok")) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
