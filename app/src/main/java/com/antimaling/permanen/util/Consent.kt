package com.antimaling.permanen.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jejak audit perintah remote.
 *
 * Ini BUKAN gerbang izin — izin Android sudah diminta sekali saat pertama
 * install (lihat MainActivity.onCreate). Fungsinya cuma mencatat perintah
 * apa saja yang pernah datang dari panel laptop, supaya pemilik HP bisa
 * memeriksanya sendiri.
 */
object Consent {

    const val LOCK = "lock"
    const val ALARM = "alarm"
    const val OVERLAY = "overlay"
    const val LOC = "loc"
    const val CAM = "cam"
    const val SHOT = "shot"
    const val SMS = "sms"

    private val LABEL = mapOf(
        LOCK to "kunci layar",
        ALARM to "dering/senter",
        OVERLAY to "banner/teks",
        LOC to "lokasi",
        CAM to "kamera",
        SHOT to "tangkapan layar",
        SMS to "kirim SMS"
    )

    fun label(cap: String): String = LABEL[cap] ?: cap

    private fun time(): String = try {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
    } catch (_: Exception) { "" }

    fun log(c: Context, action: String, detail: String, allowed: Boolean) {
        try {
            Prefs.addAudit(c, "${time()}  ${if (allowed) "IZIN " else "TOLAK"}  $action — $detail")
        } catch (_: Exception) {}
    }

    fun logCmd(c: Context, cmd: String, allowed: Boolean, note: String = "") {
        log(c, cmd, note, allowed)
    }
}
