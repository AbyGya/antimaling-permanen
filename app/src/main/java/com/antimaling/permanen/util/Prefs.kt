package com.antimaling.permanen.util

import android.content.Context

object Prefs {
    private const val N = "antimaling"
    private fun p(c: Context) = c.getSharedPreferences(N, Context.MODE_PRIVATE)

    fun getPin(c: Context): String = try { p(c).getString("pin", "1234") ?: "1234" } catch (_: Exception) { "1234" }
    fun setPin(c: Context, v: String) { try { p(c).edit().putString("pin", v.ifBlank { "1234" }).apply() } catch (_: Exception) {} }

    fun getTrusted(c: Context): String = try { p(c).getString("trusted", "") ?: "" } catch (_: Exception) { "" }
    fun setTrusted(c: Context, v: String) { try { p(c).edit().putString("trusted", v.trim()).apply() } catch (_: Exception) {} }

    fun getText(c: Context): String = try {
        p(c).getString("ctext", "HP HILANG! Hubungi pemilik. Perangkat terkunci & terlacak.") ?: ""
    } catch (_: Exception) { "HP HILANG!" }

    fun setText(c: Context, v: String) { try { p(c).edit().putString("ctext", v).apply() } catch (_: Exception) {} }

    fun isLocked(c: Context): Boolean = try { p(c).getBoolean("locked", false) } catch (_: Exception) { false }
    fun setLocked(c: Context, v: Boolean) { try { p(c).edit().putBoolean("locked", v).apply() } catch (_: Exception) {} }

    fun isOverlay(c: Context): Boolean = try { p(c).getBoolean("overlay", false) } catch (_: Exception) { false }
    fun setOverlay(c: Context, v: Boolean) { try { p(c).edit().putBoolean("overlay", v).apply() } catch (_: Exception) {} }

    fun isRinging(c: Context): Boolean = try { p(c).getBoolean("ringing", false) } catch (_: Exception) { false }
    fun setRinging(c: Context, v: Boolean) { try { p(c).edit().putBoolean("ringing", v).apply() } catch (_: Exception) {} }

    fun getSim(c: Context): String = try { p(c).getString("sim", "") ?: "" } catch (_: Exception) { "" }
    fun setSim(c: Context, v: String) { try { p(c).edit().putString("sim", v).apply() } catch (_: Exception) {} }

    // ---- Pairing laptop via MQTT (otomatis, tanpa setup) ----
    // PENTING: kode harus STABILacross "Clear storage". Kalau kode diacak ulang
    // tiap data dihapus, panel laptop langsung kehilangan HP (buguser v1.5).
    // Solusi: kode diturunkan dari Settings.Secure.ANDROID_ID yang TIDAK ikut
    // terhapus saat clear data / uninstall-install ulang.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // tanpa I/O/0/1

    /** Benih stabil per perangkat: bertahanacross clear data & reinstall. */
    fun deviceSeed(c: Context): String = try {
        val id = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        if (!id.isNullOrBlank()) id else ""
    } catch (_: Exception) { "" }

    /** Kode 8 karakter yang deterministik dari benih perangkat. */
    fun codeFromSeed(seed: String): String {
        if (seed.isBlank()) return ""
        var h = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (ch in seed.toByteArray(Charsets.UTF_8)) {
            h = h xor (ch.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        val sb = StringBuilder(8)
        var v = h
        repeat(8) {
            val idx = ((v ushr (it * 7)) and 0x7fffffff).toInt() % ALPHABET.length
            sb.append(ALPHABET[idx])
            v = v * 31 + 7
        }
        return sb.toString()
    }

    fun stableCode(c: Context): String = codeFromSeed(deviceSeed(c))

    fun normalizeCode(raw: String): String = raw.uppercase()
        .filter { ALPHABET.contains(it) }
        .take(8)

    fun isValidCode(v: String): Boolean {
        if (v.length != 8) return false
        return v.all { ALPHABET.contains(it) }
    }

    /** Kode final: pakai yang tersimpan, kalau tidak ada/tidak valid -> kode stabil. */
    fun resolveCode(c: Context): String {
        val saved = getPair(c)
        if (isValidCode(saved)) return saved
        val stable = stableCode(c)
        val code = if (stable.isNotBlank()) stable else (100000..999999).random().toString()
        setPair(c, code)
        return code
    }

    fun getPair(c: Context): String = try { p(c).getString("pair", "") ?: "" } catch (_: Exception) { "" }
    fun setPair(c: Context, v: String) { try { p(c).edit().putString("pair", v.trim()).apply() } catch (_:Exception) {} }
    fun getAndroidId(c: Context): String = try {
        var id = p(c).getString("aid", "") ?: ""
        if (id.isBlank()) { id = java.util.UUID.randomUUID().toString().take(8); p(c).edit().putString("aid", id).apply() }
        id
    } catch (_: Exception) { "hp" }

    fun getLastSync(c: Context): String = try { p(c).getString("lastsync", "-") ?: "-" } catch (_: Exception) { "-" }
    fun setLastSync(c: Context, v: String) { try { p(c).edit().putString("lastsync", v).apply() } catch (_: Exception) {} }
    // ---- Persistent stop flags (survive process kill) ----
    fun isStopRinging(c: Context): Boolean = try { p(c).getBoolean("stop_ring", false) } catch (_: Exception) { false }
    fun setStopRinging(c: Context, v: Boolean) { try { p(c).edit().putBoolean("stop_ring", v).apply() } catch (_: Exception) {} }
    fun isStopFlash(c: Context): Boolean = try { p(c).getBoolean("stop_flash", false) } catch (_: Exception) { false }
    fun setStopFlash(c: Context, v: Boolean) { try { p(c).edit().putBoolean("stop_flash", v).apply() } catch (_: Exception) {} }

    // ---- Token autentikasi respons ----
    // Broker MQTT publik bisa dibaca siapa saja. Tanpa token, orang yang berhasil
    // menebak kode bisa menyuntik pesan palsu ke panel (palsukan foto/status).
    // Token = SHA-256(kode) → panel memverifikasi tiap balasan HP.
    fun token(code: String): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(("antimaling:" + code).toByteArray(Charsets.UTF_8))
        md.digest().take(8).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    // ---- Helper generik (dipakai Consent) ----
    fun getBool(c: Context, k: String, def: Boolean): Boolean =
        try { p(c).getBoolean(k, def) } catch (_: Exception) { def }
    fun putBool(c: Context, k: String, v: Boolean) {
        try { p(c).edit().putBoolean(k, v).apply() } catch (_: Exception) {}
    }
    fun getStr(c: Context, k: String, def: String): String =
        try { p(c).getString(k, def) ?: def } catch (_: Exception) { def }
    fun putStr(c: Context, k: String, v: String) {
        try { p(c).edit().putString(k, v).apply() } catch (_: Exception) {}
    }

    // ---- Audit log: jejak semua perintah remote (dibaca pemilik di HP) ----
    fun auditLog(c: Context): List<String> = try {
        p(c).getString("audit", "")?.split("\n")?.filter { it.isNotBlank() }?.reversed() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun addAudit(c: Context, line: String) {
        try {
            val old = try { p(c).getString("audit", "") ?: "" } catch (_: Exception) { "" }
            val merged = (listOf(line) + old.split("\n").filter { it.isNotBlank() }).take(40).joinToString("\n")
            p(c).edit().putString("audit", merged).apply()
        } catch (_: Exception) {}
    }

    // ---- Panel kesehatan service ----
    // Tujuannya: kalau service mati, pengguna bisa TAHU penyebabnya
    // daripada menebak. Semua status dikumpulkan di sini.

    fun setSvcStart(c: Context) { putStr(c, "h_svcstart", now()) }
    fun getSvcStart(c: Context) = getStr(c, "h_svcstart", "belum pernah")

    /** Status startForeground terakhir. "OK" atau pesan error. */
    fun setFgStatus(c: Context, v: String) { putStr(c, "h_fg", v) }
    fun getFgStatus(c: Context) = getStr(c, "h_fg", "-")

    fun setAlarmArmed(c: Context, v: Boolean) { putBool(c, "h_alarm", v) }
    fun getAlarmArmed(c: Context) = getBool(c, "h_alarm", false)

    fun setBeat(c: Context) { putStr(c, "h_beat", now()) }
    fun getBeat(c: Context) = getStr(c, "h_beat", "belum pernah")

    fun setCamError(c: Context, v: String) { putStr(c, "h_cam", v) }
    fun getCamError(c: Context) = getStr(c, "h_cam", "-")

    fun setJobArmed(c: Context, v: Boolean) { putBool(c, "h_job", v) }
    fun getJobArmed(c: Context) = getBool(c, "h_job", false)

    fun setLastPhoto(c: Context, v: String) { putStr(c, "h_photo", v) }
    fun getLastPhoto(c: Context) = getStr(c, "h_photo", "belum pernah")

    // ---- Percobaan buka gagal (trigger foto + siren otomatis) ----
    fun getWrongPin(c: Context): Int = try { p(c).getInt("wrong_pin", 0) } catch (_: Exception) { 0 }
    fun setWrongPin(c: Context, v: Int) { try { p(c).edit().putInt("wrong_pin", v).apply() } catch (_: Exception) {} }
    fun resetWrongPin(c: Context) { setWrongPin(c, 0) }

    // ---- Keadaan jebakan: sudah berbunyi untuk kejadian ini ----
    fun getTheftFired(c: Context): Boolean = getBool(c, "theft_fired", false)
    fun setTheftFired(c: Context, v: Boolean) { putBool(c, "theft_fired", v) }
    fun resetTheft(c: Context) {
        setWrongPin(c, 0)
        setTheftFired(c, false)
    }

    private fun now(): String = try {
        java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US).format(java.util.Date())
    } catch (_: Exception) { "-" }
}
