package com.antimaling.permanen.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * JobService jaring kedua — dipanggil JobScheduler supaya app dibangunkan
 * kembali walau proses sudah dibunuh / dibekukan XOS.
 *
 * BUG YANG SUDAH DIPERBAIKI: versi sebelumnya专项资金 pekerjaan di dalam
 * Thread terpisah lalu memanggil jobFinished() dari thread itu. Itu menghasilkan
 * ANR "No response to onStartJob" (tercatat di log HP 29/09 12:40) karena:
 *  - jobFinished() yang dipanggil manual setelah system menganggap job selesai
 *    bisa menggantung, terutama kalau proses sedang dibekukan XOS
 *    (mHiberReason='frozen' pada log).
 *
 * Sekarang: semua pekerjaan TAMBAT dan SINKRON langsung di onStartJob.
 * Semuanya cuma startService() + schedule() yang tidak blocking, jadi selesai
 * dalam hitungan milidetik dan tidak pernah menyentuh jaringan.
 */
class KeepAliveJob : JobService() {

    override fun onStartJob(p: JobParameters?): Boolean {
        // Kerjakan seketika & sinkron. Semua operasi di bawah non-blocking
        // (hanya mengirim Intent / menjadwalkan job), tidak ada I/O atau sleep.
        try {
            KeepAlive.pulse(applicationContext)
        } catch (e: Exception) {
            Log.w("KeepAliveJob", "pulse gagal: ${e.message}")
        }
        // false = job selesai di sini; sistem yang menutupnya.
        // JANGAN panggil jobFinished() manual setelah mengembalikan false.
        return false
    }

    override fun onStopJob(p: JobParameters?): Boolean {
        // true = minta dijadwalkan ulang. pulse() juga menjadwalkan ulang
        // sendiri sebagai jaring pengaman.
        return true
    }
}
