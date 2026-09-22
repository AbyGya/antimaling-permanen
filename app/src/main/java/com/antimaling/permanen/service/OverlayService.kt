package com.antimaling.permanen.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.antimaling.permanen.AntiMalApp
import com.antimaling.permanen.util.Prefs

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var view: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try { startForeground(102, notif()) } catch (_: Exception) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        running = true
        try { startForeground(102, notif()) } catch (_: Exception) {}
        when (i?.action) {
            "OFF" -> { hide(); stopSelf(); return START_NOT_STICKY }
            else -> show()
        }
        return START_STICKY
    }

    private fun show() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
            hide()
            val text = Prefs.getText(this)
            val tv = TextView(this).apply {
                setText("🔒 $text")
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(28, 36, 28, 36)
                setBackgroundColor(0xE6B71C1C.toInt())
            }
            val type = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }
            wm = getSystemService(WindowManager::class.java)
            wm?.addView(tv, lp)
            view = tv
        } catch (_: Exception) {}
    }

    private fun hide() {
        try { if (view != null) wm?.removeView(view) } catch (_: Exception) {}
        view = null
    }

    private fun notif(): Notification =
        NotificationCompat.Builder(this, AntiMalApp.CH_OVERLAY)
            .setContentTitle("Overlay AntiMaling")
            .setContentText("Peringatan tampil di home/lock")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

    override fun onTaskRemoved(root: Intent?) {
        // disapu dari recents: hidupkan lagi bila masih dibutuhkan
        try {
            if (Prefs.isOverlay(this) || Prefs.isLocked(this)) restart(this)
        } catch (_: Exception) {}
        super.onTaskRemoved(root)
    }

    override fun onDestroy() {
        running = false
        try { hide() } catch (_: Exception) {}
        // jika masih diminta ON (terkunci), hidup lagi — anti dibunuh maling
        try {
            if (Prefs.isOverlay(this) || Prefs.isLocked(this)) restart(this)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false

        fun restart(c: Context) {
            try {
                if (!Prefs.isOverlay(c) && !Prefs.isLocked(c)) {
                    try { c.stopService(Intent(c, OverlayService::class.java)) } catch (_: Exception) {}
                    return
                }
                val it = Intent(c, OverlayService::class.java).apply { action = "ON" }
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(it) else c.startService(it)
            } catch (_: Exception) {}
        }
    }
}
