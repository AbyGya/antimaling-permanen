package com.antimaling.permanen.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antimaling.permanen.util.Prefs
import java.util.concurrent.TimeUnit

/**
 * Dua jaring pengaman, dipakai bersamaan:
 *
 *  1. WorkManager periodik 15 menit — andal, tapi XOS/Infinix sering
 *     menunda atau membatalkan jadwalnya sendiri.
 *  2. JobScheduler — biasanya lebih cepat dan lebih dipatuhi OEM
 *     dibanding WorkManager.
 *
 * Kenapa dua-duanya: kalau salah satu diblokir OEM, yang lain masih hidup.
 * Kalau keduanya diblokir, whitelist XOS (lihat README) yang penentunya.
 */
object KeepAlive {

    private const val TAG = "KeepAlive"
    private const val JOB_ID = 4201

    fun scheduleJob(c: Context) {
        try {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            val extras = PersistableBundle().apply {
                putString("job", "antimaling-keepalive")
            }
            val info = JobInfo.Builder(JOB_ID, android.content.ComponentName(c, KeepAliveJob::class.java))
                .setPersisted(true)
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPeriodic(15 * 60 * 1000L)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            val res = js.schedule(info)
            Prefs.setJobArmed(c, res == JobScheduler.RESULT_SUCCESS)
            if (res != JobScheduler.RESULT_SUCCESS) Log.w(TAG, "JobScheduler ditolak: $res")
        } catch (e: Exception) {
            try { Prefs.setJobArmed(c, false) } catch (_: Exception) {}
            Log.w(TAG, "gagal jadwalkan job: ${e.message}")
        }
    }

    fun cancelJob(c: Context) {
        try { c.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) } catch (_: Exception) {}
        try { Prefs.setJobArmed(c, false) } catch (_: Exception) {}
    }

    /** Dipanggil dari JobScheduler: bangunkan semua yang vital. */
    fun pulse(c: Context) {
        try { GuardService.start(c) } catch (_: Exception) {}
        try { com.antimaling.permanen.net.MqttLink.start(c) } catch (_: Exception) {}
        try {
            if (Prefs.isOverlay(c) || Prefs.isLocked(c)) OverlayService.restart(c)
        } catch (_: Exception) {}
        // Jaring kedua: setiap job selesai, jadwalkan lagi supaya tidak berhenti
        scheduleJob(c)
        scheduleWorker(c)
    }

    fun scheduleWorker(c: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "keep", ExistingPeriodicWorkPolicy.KEEP, req
            )
        } catch (_: Exception) {}
    }
}
