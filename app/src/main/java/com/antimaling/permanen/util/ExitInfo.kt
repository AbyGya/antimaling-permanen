package com.antimaling.permanen.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Membaca alasan kenapa process aplikasi ini dibunuh.
 *
 * Ini penting karena "service mati" punya banyak sebab yang gejalanya sama,
 * dan tanpa data ini kita cuma menebak. Android 11+ (API 30+) menyediakan
 * ApplicationExitInfo yang mencatat alasan kematian + timestamp + deskripsi
 * sistem — termasuk info yang tidak akan pernah kita lihat dari dalam app
 * (mis. SIGNAL dari OEM killer).
 *
 * Tidak butuh izin khusus untuk membaca exit info milik app sendiri.
 */
object ExitInfo {

    data class Report(
        val reason: String,
        val at: String,
        val detail: String,
        val importance: Int
    )

    /** reports: yang terbaru dulu. */
    fun reports(c: Context, max: Int = 5): List<Report> {
        return try {
            if (Build.VERSION.SDK_INT < 30) return emptyList()
            val am = c.getSystemService(ActivityManager::class.java) ?: return emptyList()
            am.getHistoricalProcessExitReasons(c.packageName, 0, max)
                .map { it.toReport() }
        } catch (_: Exception) { emptyList() }
    }

    fun latest(c: Context): Report? = reports(c, 1).firstOrNull()

    private fun ApplicationExitInfo.toReport(): Report = Report(
        reason = label(reason),
        at = timeText(),
        detail = if (Build.VERSION.SDK_INT >= 30) (description ?: "") else "",
        importance = importance
    )

    private fun ApplicationExitInfo.timeText(): String = try {
        val t = timestamp / 1000
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US)
        fmt.format(java.util.Date(t))
    } catch (_: Exception) { "?" }

    private fun label(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_ANR -> "HANG (ANR) — app macet, sistem membunuhnya"
        ApplicationExitInfo.REASON_CRASH -> "CRASH — ada bug di app (INI BISA KITA PERBAIKI)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH native"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "BOROS resource"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Keluar sendiri (normal)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Gagal inisialisasi"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "MEMORI HABIS — sistem membunuhnya karena RAM"
        ApplicationExitInfo.REASON_OTHER -> "LAIN-LAIN (biasanya OEM killer / XOS)"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Izin berubah"
        ApplicationExitInfo.REASON_SIGNALED -> "DIBUNUH SINYAL (SIGKILL dari sistem/OEM)"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER — swipe dari Recents / Force Stop"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER — app dihentikan paksa"
        else -> "Alasan #$r (tidak dikenal)"
    }

    /** Ringkasan singkat untuk panel kesehatan. */
    fun summary(c: Context): String {
        val r = latest(c) ?: return "Android < 11, tidak ada riwayat"
        return "${r.reason}\n   ${r.at}"
    }
}
