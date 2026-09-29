package com.antimaling.permanen.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * JobService jaring kedua. Dipanggil JobScheduler supaya service dibangunkan
 * ulang walau proses sudah dibunuh OEM. Sengaja sangat tipis — semua
 * pekerjaanTaruh di KeepAlive.pulse() supaya mudah diuji.
 */
class KeepAliveJob : JobService() {

    override fun onStartJob(p: JobParameters?): Boolean {
        // false = pekerjaan selesai sebelum selesai (tidak perlu pekerjaan lanjutan)
        return try {
            Thread {
                KeepAlive.pulse(applicationContext)
                try { jobFinished(p, false) } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
            false
        } catch (e: Exception) {
            Log.w("KeepAliveJob", "gagal: ${e.message}")
            false
        }
    }

    override fun onStopJob(p: JobParameters?): Boolean {
        // true = dijadwalkan ulang otomatis; kita juga jadwalkan manual di pulse()
        return true
    }
}
