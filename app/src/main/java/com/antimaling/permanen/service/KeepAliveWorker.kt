package com.antimaling.permanen.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antimaling.permanen.util.Prefs

class KeepAliveWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        return try {
            try { GuardService.start(applicationContext) } catch (_: Exception) {}
            try { com.antimaling.permanen.net.MqttLink.start(applicationContext) } catch (_: Exception) {}
            try {
                if (Prefs.isOverlay(applicationContext) || Prefs.isLocked(applicationContext))
                    OverlayService.restart(applicationContext)
            } catch (_: Exception) {}
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
