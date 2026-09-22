package com.antimaling.permanen.spy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.antimaling.permanen.receiver.MyAdminReceiver
import com.antimaling.permanen.util.Prefs

object DeviceInfo {
    fun text(c: Context): String {
        return try {
            val batt = try {
                val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (l >= 0) "${(l * 100 / s)}%" else "?"
            } catch (_: Exception) { "?" }
            val dpm = try { c.getSystemService(DevicePolicyManager::class.java) } catch (_: Exception) { null }
            val admin = try { dpm?.isAdminActive(ComponentName(c, MyAdminReceiver::class.java)) == true } catch (_: Exception) { false }
            "📱 ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "🤖 Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "🔋 Baterai $batt\n" +
                    "🛡 Admin: ${if (admin) "AKTIF" else "MATI"} | Overlay: ${if (Prefs.isOverlay(c)) "ON" else "OFF"}\n" +
                    "🔒 Terkunci: ${if (Prefs.isLocked(c)) "YA" else "tidak"} | Dering: ${if (Prefs.isRinging(c)) "YA" else "tidak"}\n" +
                    "📷 Shot: ${if (ShotTaker.hasConsent()) "siap" else "butuh izin"}"
        } catch (_: Exception) { "Gagal baca info." }
    }
}
