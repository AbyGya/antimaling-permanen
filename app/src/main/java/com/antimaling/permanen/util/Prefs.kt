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

    // ---- Cloud (panel laptop via Firebase REST) ----
    fun getFbKey(c: Context): String = try { p(c).getString("fbkey", "") ?: "" } catch (_: Exception) { "" }
    fun setFbKey(c: Context, v: String) { try { p(c).edit().putString("fbkey", v.trim()).apply() } catch (_: Exception) {} }
    fun getFbProject(c: Context): String = try { p(c).getString("fbproject", "") ?: "" } catch (_: Exception) { "" }
    fun setFbProject(c: Context, v: String) { try { p(c).edit().putString("fbproject", v.trim()).apply() } catch (_: Exception) {} }
    fun cloudOn(c: Context): Boolean = try {
        getFbKey(c).isNotBlank() && getFbProject(c).isNotBlank() && getPair(c).isNotBlank()
    } catch (_: Exception) { false }

    fun getPair(c: Context): String = try { p(c).getString("pair", "") ?: "" } catch (_: Exception) { "" }
    fun setPair(c: Context, v: String) { try { p(c).edit().putString("pair", v.trim()).apply() } catch (_:Exception) {} }
    fun getAndroidId(c: Context): String = try {
        var id = p(c).getString("aid", "") ?: ""
        if (id.isBlank()) { id = java.util.UUID.randomUUID().toString().take(8); p(c).edit().putString("aid", id).apply() }
        id
    } catch (_: Exception) { "hp" }

    fun getIdToken(c: Context): String = try { p(c).getString("idtoken", "") ?: "" } catch (_: Exception) { "" }
    fun setIdToken(c: Context, v: String) { try { p(c).edit().putString("idtoken", v).apply() } catch (_: Exception) {} }
    fun getRefresh(c: Context): String = try { p(c).getString("refresh", "") ?: "" } catch (_: Exception) { "" }
    fun setRefresh(c: Context, v: String) { try { p(c).edit().putString("refresh", v).apply() } catch (_: Exception) {} }
    fun getTokenExp(c: Context): Long = try { p(c).getLong("tokenexp", 0) } catch (_: Exception) { 0 }
    fun setTokenExp(c: Context, v: Long) { try { p(c).edit().putLong("tokenexp", v).apply() } catch (_: Exception) {} }
    fun getLastSync(c: Context): String = try { p(c).getString("lastsync", "-") ?: "-" } catch (_: Exception) { "-" }
    fun setLastSync(c: Context, v: String) { try { p(c).edit().putString("lastsync", v).apply() } catch (_: Exception) {} }
}
