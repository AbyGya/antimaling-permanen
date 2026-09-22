package com.antimaling.permanen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antimaling.permanen.service.GuardService
import com.antimaling.permanen.service.KeepAliveWorker
import com.antimaling.permanen.service.OverlayService
import com.antimaling.permanen.util.Prefs
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        try {
            val a = i?.action ?: ""
            val ok = a.contains("BOOT_COMPLETED") || a.contains("QUICKBOOT") ||
                    a.contains("MY_PACKAGE_REPLACED") || a.contains("PACKAGE_REPLACED") ||
                    a == Intent.ACTION_LOCKED_BOOT_COMPLETED
            if (!ok && a.isNotEmpty()) return
            try { GuardService.start(c) } catch (_: Exception) {}
            try {
                if (Prefs.isOverlay(c) || Prefs.isLocked(c)) OverlayService.restart(c)
            } catch (_: Exception) {}
            try {
                val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                    "keep", ExistingPeriodicWorkPolicy.KEEP, req
                )
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
