package com.antimaling.permanen.control

import android.content.Context
import com.antimaling.permanen.service.CamService
import com.antimaling.permanen.util.Prefs

/**
 * Jebakan otomatis saat ada percobaan pencurian.
 *
 * Dipicu dari dua tempat:
 *  - PIN salah berulang di layar kunci
 *  - SIM dicabut (lihat SimChangeReceiver)
 *
 * Aksi: foto + sirene. Sengaja dipanggil dari konteks
 * Activity yang terlihat (layar kunci), karena Android hanya mengizinkan
 * kamera saat ada window app yang tampil.
 */
object TheftGuard {

    /** Ambang PIN salah sebelum jebakan aktif. */
    const val PIN_THRESHOLD = 3

    /**
     * Dipanggil setiap PIN salah. Kembalikan jumlah percobaan terbaru
     * supaya UI bisa memberi tahu pengguna.
     */
    fun onWrongPin(c: Context): Int {
        val n = try { Prefs.getWrongPin(c) + 1 } catch (_: Exception) { 1 }
        try { Prefs.setWrongPin(c, n) } catch (_: Exception) {}
        if (n >= PIN_THRESHOLD) trigger(c, "PIN salah $n×")
        return n
    }

    /** Dipanggil saat SIM dicabut. */
    fun onSimRemoved(c: Context) {
        trigger(c, "SIM dicabut")
    }

    /**
     * Aktifkan jebakan: foto + sirene. Tidak berulang untuk kejadian yang sama
     * supaya tidak boros baterai kalau pencuri mencoba terus.
     */
    fun trigger(c: Context, reason: String) {
        try {
            if (Prefs.getTheftFired(c)) return
            Prefs.setTheftFired(c, true)
        } catch (_: Exception) {}

        // Siren dulu (langsung beresonansi), lalu foto di background.
        try { RingManager.start(c) } catch (_: Exception) {}

        try {
            CamService.snap(c, front = true, reason = reason, alsoRing = false)
        } catch (_: Exception) {}

        // Kabari pemilik langsung kalau nomor tersedia
        try {
            val to = Prefs.getTrusted(c)
            if (to.isNotBlank()) {
                CommandHandler.reply(
                    c, to,
                    "🚨 AntiMaling: $reason!\nHP dikunci & sirene berbunyi.\nKirim #LOCATE untuk posisi, #UNLOCK#pin untuk buka."
                )
            }
        } catch (_: Exception) {}

        try {
            val f = LocateManager.snapshot(c)
            if (f != null) {
                com.antimaling.permanen.net.MqttLink.push(
                    "alert", "🚨 JEBAKAN: $reason\n📍 ${f.lat}, ${f.lon} (±${f.acc.toInt()}m)\n${f.url}",
                    lat = f.lat, lon = f.lon, acc = f.acc
                )
            } else {
                com.antimaling.permanen.net.MqttLink.push("alert", "🚨 JEBAKAN: $reason")
            }
        } catch (_: Exception) {}
    }

    /** Dipanggil setelah owner berhasil buka kunci. */
    fun clear(c: Context) {
        try { Prefs.resetTheft(c) } catch (_: Exception) {}
    }
}
