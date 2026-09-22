package com.antimaling.permanen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AntiMalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Anti-FC: jangan biarkan crash loop membunuh service permanen
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { e.printStackTrace() } catch (_: Exception) {}
            // jangan kill brutal — biarkan sistem restart service START_STICKY
            try { def?.uncaughtException(t, e) } catch (_: Exception) {}
        }
        try { makeChannels() } catch (_: Exception) {}
    }

    private fun makeChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val list = listOf(
            NotificationChannel("guard", "Proteksi Aktif", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("alarm", "Alarm Darurat", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("overlay", "Overlay Info", NotificationManager.IMPORTANCE_LOW)
        )
        list.forEach { try { nm.createNotificationChannel(it) } catch (_: Exception) {} }
    }

    companion object {
        const val CH_GUARD = "guard"
        const val CH_ALARM = "alarm"
        const val CH_OVERLAY = "overlay"
    }
}
