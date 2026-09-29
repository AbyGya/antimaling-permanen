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
import com.antimaling.permanen.util.Consent
import com.antimaling.permanen.util.Prefs

object CommandHandler {

    data class CloudResult(
        val text: String,
        val image: String = "",
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val acc: Float = 0f
    )

    /** Broadcast agar LockActivity yang sedang tampil langsung tutup. */
    const val UNLOCK_ACTION = "com.antimaling.permanen.UNLOCK"

    private fun announceUnlock(c: Context) {
        try { c.sendBroadcast(Intent(UNLOCK_ACTION)) } catch (_: Exception) {}
    }

    /**
     * Pemetaan perintah -> kapabilitas yang dibutuhkan, untuk dicatat di audit log saja.
     */
    private fun needOf(type: String): String? = when (type.lowercase()) {
        "lock", "unlock" -> Consent.LOCK
        "ring", "flash" -> Consent.ALARM
        "text", "overlay" -> Consent.OVERLAY
        "locate", "evidence" -> Consent.LOC
        "shot" -> Consent.SHOT
        "photo" -> Consent.CAM
        "sms" -> Consent.SMS
        else -> null
    }

    /**
     * Eksekutor perintah dari panel laptop.
     *
     * Izin Android sudah diminta sekali saat pertama install (lihat MainActivity),
     * jadi di sini tidak ada gate tambahan — cukup catat jejak perintah supaya
     * pemilik bisa melihat apa saja yang pernah dikontrol dari laptop.
     */
    fun execCloud(c: Context, type: String, arg: String): List<CloudResult> {
        val t = type.lowercase()
        Consent.logCmd(c, t, true, if (arg.isBlank()) "" else "arg=" + arg.take(40))
        return execGranted(c, t, arg)
    }

    private fun execGranted(c: Context, type: String, arg: String): List<CloudResult> = try {
        when (type) {
            "lock" -> listOf(CloudResult("🔒 HP dikunci + overlay ON.").also { lock(c) })
            "unlock" -> listOf(
                if (arg.trim() == Prefs.getPin(c)) { unlock(c); CloudResult("🔓 Kunci dibuka.") }
                else CloudResult("❌ PIN salah.")
            )
            "ring" -> listOf(CloudResult("🔊 Dering MAX dimulai.").also { RingManager.start(c) })
            "locate" -> listOf(locateResult(c))
            "flash" -> {
                val s = arg.filter { it.isDigit() }.toIntOrNull() ?: 60
                FlashManager.start(c, s.coerceIn(5, 300)); RingManager.start(c)
                listOf(CloudResult("🔦 Senter kedip + dering ${s.coerceIn(5, 300)} dtk."))
            }
            "text" -> listOf(
                if (arg.isBlank()) CloudResult("❌ Teks kosong.")
                else { Prefs.setText(c, arg); OverlayService.restart(c); CloudResult("✏️ Teks overlay diganti.") }
            )
            "overlay" -> {
                val on = arg.lowercase().contains("on")
                Prefs.setOverlay(c, on); OverlayService.restart(c)
                listOf(CloudResult("🖼 Overlay ${if (on) "ON" else "OFF"}."))
            }
            "shot" -> listOf(
                ShotTaker.shot(c).let { r ->
                    if (r.image.isEmpty()) CloudResult("❌ ${r.err.ifBlank { "Screenshot gagal." }}")
                    else CloudResult("📸 Screenshot layar:", r.image)
                }
            )
            "photo" -> {
                val front = !arg.lowercase().contains("back")
                listOf(CamSnap.shoot(c, front).let { r ->
                    if (r.image.isEmpty()) CloudResult("❌ ${r.err.ifBlank { "Foto gagal." }}")
                    else CloudResult(if (front) "📷 Kamera depan:" else "📷 Kamera belakang:", r.image)
                })
            }
            "sms" -> {
                val to = arg.substringBefore(' ').trim()
                val body = arg.substringAfter(' ', "").trim()
                if (to.isBlank() || body.isBlank()) listOf(CloudResult("❌ Format: sms <nomor> <pesan>"))
                else { reply(c, to, body); listOf(CloudResult("📤 SMS dikirim ke $to.")) }
            }
            // 1-klik bukti pencurian: lokasi + screenshot + 2 foto, terkirim berurutan
            "evidence" -> {
                val out = ArrayList<CloudResult>()
                out.add(locateResult(c))
                ShotTaker.shot(c).let { r ->
                    if (r.image.isNotEmpty()) out.add(CloudResult("📸 Bukti — screenshot:", r.image))
                }
                for (front in listOf(true, false)) {
                    CamSnap.shoot(c, front).let { r ->
                        if (r.image.isNotEmpty())
                            out.add(CloudResult(if (front) "📷 Bukti — kamera depan:" else "📷 Bukti — kamera belakang:", r.image))
                    }
                }
                out.add(CloudResult(DeviceInfo.text(c)))
                if (out.size <= 1) out.add(CloudResult("⚠️ Bukti terbatas — cek izin kamera & lokasi."))
                out
            }
            "info" -> listOf(CloudResult(DeviceInfo.text(c)))
            // --- Foto pencuri di layar kunci ---
            "setphoto" -> {
                val err = com.antimaling.permanen.util.MalingPhoto.saveFromBase64(c, arg)
                listOf(
                    if (err.isBlank())
                        CloudResult("🖼 Foto pencuri tersimpan & TAMPIL DI LAYAR KUNCI.\nKalau layar kunci sedang terbuka, tekan Kunci lagi untuk melihatnya.")
                    else CloudResult("❌ Gagal simpan foto: $err")
                )
            }
            "clearsphoto" -> {
                val ok = com.antimaling.permanen.util.MalingPhoto.delete(c)
                listOf(if (ok) CloudResult("🗑 Foto pencuri dihapus dari layar kunci.") else CloudResult("⚠️ Tidak ada foto untuk dihapus."))
            }
            "showphoto" -> {
                val has = com.antimaling.permanen.util.MalingPhoto.exists(c)
                listOf(
                    if (has) CloudResult("🖼 Foto pencuri AKTIF di layar kunci.")
                    else CloudResult("ℹ️ Belum ada foto pencuri. Kirim dulu lewat tombol 'Kirim Foto Maling' di panel.")
                )
            }
            "warn" -> {
                val t = arg.trim()
                if (t.isBlank()) listOf(CloudResult("❌ Teks kosong."))
                else {
                    Prefs.setText(c, t)
                    Prefs.putStr(c, "warn_text", t)
                    OverlayService.restart(c)
                    listOf(CloudResult("✏️ Teks ancaman di layar kunci diganti."))
                }
            }
            else -> listOf(CloudResult("❓ Perintah '$type' tidak dikenal."))
        }
    } catch (e: Exception) { listOf(CloudResult("⚠️ Error: ${e.message}")) }

    private fun locateResult(c: Context): CloudResult {
        val s = LocateManager.snapshot(c)
        if (s == null) return CloudResult(LocateManager.link(c))
        return CloudResult("📍 Lokasi (±${s.acc.toInt()}m): ${s.url}", "", s.lat, s.lon, s.acc)
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
            try { LockActivity.show(c) } catch (_: Exception) {}
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
            // pemilik kembali: matikan juga banner overlay + tutup layar kunci
            try {
                Prefs.setOverlay(c, false)
                c.stopService(Intent(c, OverlayService::class.java))
            } catch (_: Exception) {}
            announceUnlock(c)
        } catch (_: Exception) {}
    }

    fun stopAll(c: Context) {
        try {
            // stop hanya hentikan suara/senter — kunci tetap bila masih terkunci
            stopAlarmOnly(c)
            try { LockNotifier.cancel(c) } catch (_: Exception) {}
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
