package com.antimaling.permanen.control

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.receiver.MyAdminReceiver
import com.antimaling.permanen.service.GuardService
import com.antimaling.permanen.service.OverlayService
import com.antimaling.permanen.spy.CamSnap
import com.antimaling.permanen.spy.DeviceInfo
import com.antimaling.permanen.spy.ShotTaker
import com.antimaling.permanen.util.Prefs

object CommandHandler {

    data class CloudResult(val text: String, val image: String = "")

    /** Eksekutor perintah dari panel laptop (tanpa butuh PIN, channel sudah privat per pairing). */
    fun execCloud(c: Context, type: String, arg: String): CloudResult {
        return try {
            when (type.lowercase()) {
                "lock" -> { lock(c); CloudResult("🔒 HP dikunci + overlay ON.") }
                "unlock" -> {
                    if (arg == Prefs.getPin(c)) { unlock(c); CloudResult("🔓 Kunci dibuka.") }
                    else CloudResult("❌ PIN salah.")
                }
                "ring" -> { RingManager.start(c); CloudResult("🔊 Dering MAX dimulai.") }
                "stop" -> { stopAll(c); CloudResult("🔇 Semua alarm berhenti.") }
                "locate" -> CloudResult("📍 " + LocateManager.lastText(c))
                "flash" -> {
                    val s = arg.filter { it.isDigit() }.toIntOrNull() ?: 60
                    FlashManager.start(c, s.coerceIn(5, 300)); RingManager.start(c)
                    CloudResult("🔦 Senter kedip + dering ${s.coerceIn(5, 300)} dtk.")
                }
                "text" -> {
                    if (arg.isBlank()) CloudResult("❌ Teks kosong.")
                    else { Prefs.setText(c, arg); OverlayService.restart(c); CloudResult("✏️ Teks overlay diganti.") }
                }
                "overlay" -> {
                    val on = arg.lowercase().contains("on")
                    Prefs.setOverlay(c, on); OverlayService.restart(c)
                    CloudResult("🖼 Overlay ${if (on) "ON" else "OFF"}.")
                }
                "shot" -> {
                    val img = ShotTaker.capture(c)
                    if (img == null) CloudResult("❌ Screenshot gagal. Beri izin 'Sadap Layar' di app HP (hangus tiap reboot).")
                    else CloudResult("📸 Screenshot layar:", img)
                }
                "photo" -> {
                    val front = !arg.lowercase().contains("back")
                    val img = CamSnap.snap(c, front)
                    if (img == null) CloudResult("❌ Foto gagal (cek izin kamera).")
                    else CloudResult(if (front) "📷 Kamera depan:" else "📷 Kamera belakang:", img)
                }
                "info" -> CloudResult(DeviceInfo.text(c))
                "ping" -> CloudResult("✅ HP online.")
                else -> CloudResult("❓ Perintah '$type' tidak dikenal.")
            }
        } catch (e: Exception) { CloudResult("⚠️ Error: ${e.message}") }
    }

    fun isAuthorized(c: Context, sender: String?, body: String): Boolean {
        return try {
            val trusted = Prefs.getTrusted(c).trim()
            val pin = Prefs.getPin(c)
            if (trusted.isBlank()) return true // mode awal: terima semua agar "tidak no-fungsi"
            val norm = { s: String -> s.replace("[^0-9+]".toRegex(), "") }
            if (sender != null && norm(sender).endsWith(norm(trusted).takeLast(8))) return true
            body.contains(pin) // PIN benar = otorisasi darurat dari HP lain
        } catch (_: Exception) { true }
    }

    fun handleSms(c: Context, sender: String?, raw: String): Boolean {
        return try {
            val body = raw.trim().uppercase()
            if (!body.startsWith("#")) return false
            if (!isAuthorized(c, sender, raw)) {
                reply(c, sender, "AntiMaling: tidak diotorisasi.")
                return true
            }
            when {
                body.startsWith("#LOCK") -> { lock(c); reply(c, sender, "AntiMaling: HP DIKUNCI. ${Prefs.getText(c)}"); true }
                body.startsWith("#UNLOCK") -> {
                    val pin = extractArg(raw, "#UNLOCK")
                    if (pin == Prefs.getPin(c)) { unlock(c); reply(c, sender, "AntiMaling: dibuka."); }
                    else reply(c, sender, "AntiMaling: PIN salah.")
                    true
                }
                body.startsWith("#RING") -> { RingManager.start(c); reply(c, sender, "AntiMaling: dering MAX dimulai. Kirim #STOP#pin untuk henti."); true }
                body.startsWith("#STOP") -> {
                    val pin = extractArg(raw, "#STOP")
                    if (pin == Prefs.getPin(c) || Prefs.getTrusted(c).isBlank()) { stopAll(c); reply(c, sender, "AntiMaling: alarm berhenti.") }
                    else reply(c, sender, "AntiMaling: PIN salah, alarm tetap bunyi.")
                    true
                }
                body.startsWith("#LOCATE") -> { LocateManager.requestAndReply(c, sender); true }
                body.startsWith("#FLASH") -> {
                    val arg = extractArg(raw, "#FLASH").filter { it.isDigit() }.toIntOrNull() ?: 60
                    FlashManager.start(c, arg.coerceIn(5, 300)); RingManager.start(c)
                    reply(c, sender, "AntiMaling: senter kedip $arg dtk + dering.")
                    true
                }
                body.startsWith("#TEXT#") -> {
                    // case-insensitive: "#text#halo" tetap kebaca
                    val idx = raw.uppercase().indexOf("#TEXT#")
                    val t = if (idx >= 0) raw.substring(idx + 6).trim().trimStart('#', ' ', ':').trim() else ""
                    if (t.isNotEmpty()) { Prefs.setText(c, t); OverlayService.restart(c); reply(c, sender, "AntiMaling: teks overlay diganti.") }
                    true
                }
                body.startsWith("#OVERLAY#") -> {
                    val idx = raw.uppercase().indexOf("#OVERLAY#")
                    val arg = if (idx >= 0) raw.substring(idx + 9).trim().trimStart('#', ' ', ':').trim() else ""
                    val on = arg.uppercase().startsWith("ON")
                    Prefs.setOverlay(c, on); OverlayService.restart(c)
                    reply(c, sender, "AntiMaling: overlay ${if (on) "ON" else "OFF"}.")
                    true
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun extractArg(raw: String, prefix: String): String {
        return try {
            var s = raw.trim()
            // dukung #UNLOCK#1234 dan #UNLOCK 1234
            s = s.replace(prefix, "", ignoreCase = true).trim().trimStart('#', ' ', ':')
            s.trim().split(" ", "#")[0].trim()
        } catch (_: Exception) { "" }
    }

    fun lock(c: Context) {
        try {
            Prefs.setLocked(c, true)
            Prefs.setOverlay(c, true)
            // kunci sistem (butuh admin) — gagal pun overlay tetap jalan (anti no-fungsi)
            try {
                val dpm = c.getSystemService(DevicePolicyManager::class.java)
                val cn = ComponentName(c, MyAdminReceiver::class.java)
                if (dpm != null && dpm.isAdminActive(cn)) dpm.lockNow()
            } catch (_: Exception) {}
            // WAJIB dari background: notifikasi full-screen (selalu diizinkan sistem).
            // startActivity/startForegroundService langsung diblokir saat HP idle.
            try { LockNotifier.fire(c) } catch (_: Exception) {}
            try { GuardService.start(c) } catch (_: Exception) {}
            try { OverlayService.restart(c) } catch (_: Exception) {}
            try {
                val i = Intent(c, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                c.startActivity(i)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /** Dipanggil LockActivity saat tampil: sekarang boleh start service (konteks foreground). */
    fun onLockShown(c: Context) {
        try {
            Prefs.setLocked(c, true)
            Prefs.setOverlay(c, true)
            try { GuardService.start(c) } catch (_: Exception) {}
            try { OverlayService.restart(c) } catch (_: Exception) {}
            try { LockNotifier.fire(c) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun unlock(c: Context) {
        try {
            Prefs.setLocked(c, false)
            stopAlarmOnly(c)
            try { LockNotifier.cancel(c) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun stopAll(c: Context) {
        try {
            Prefs.setLocked(c, false)
            stopAlarmOnly(c)
            Prefs.setOverlay(c, false)
            try { LockNotifier.cancel(c) } catch (_: Exception) {}
            try { c.stopService(Intent(c, OverlayService::class.java)) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun stopAlarmOnly(c: Context) {
        try { RingManager.stop(c) } catch (_: Exception) {}
        try { FlashManager.stop(c) } catch (_: Exception) {}
    }

    fun reply(c: Context, to: String?, msg: String) {
        try {
            if (to.isNullOrBlank()) return
            val sms = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms?.sendTextMessage(to, null, msg.take(150), null, null)
        } catch (_: Exception) {}
    }

    fun canDrawOverlay(c: Context): Boolean =
        try { Settings.canDrawOverlays(c) } catch (_: Exception) { false }
}
