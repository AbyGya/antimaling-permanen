package com.antimaling.permanen.control

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.antimaling.permanen.util.Perms

object LocateManager {

    fun lastText(c: Context): String {
        return try {
            if (!Perms.location(c)) return "AntiMaling: izin lokasi belum diberikan. Aktifkan di panel pemilik."
            val lm = c.getSystemService(LocationManager::class.java) ?: return "AntiMaling: GPS tidak tersedia."
            var loc: Location? = null
            try { loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
            if (loc == null) { try { loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) {} }
            if (loc == null) { try { loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) {} }
            if (loc == null) "AntiMaling: lokasi belum siap, coba #LOCATE lagi 30 dtk."
            else fmt(loc)
        } catch (e: SecurityException) { "AntiMaling: izin lokasi ditolak." }
        catch (_: Exception) { "AntiMaling: gagal ambil lokasi." }
    }

    @SuppressLint("MissingPermission")
    fun requestAndReply(c: Context, to: String?) {
        try {
            CommandHandler.reply(c, to, lastText(c))
            if (!Perms.location(c)) return
            val lm = c.getSystemService(LocationManager::class.java) ?: return
            var done = false
            val lis = object : LocationListener {
                override fun onLocationChanged(l: Location) {
                    if (done) return; done = true
                    try { CommandHandler.reply(c, to, "AntiMaling UPDATE: " + fmt(l)) } catch (_: Exception) {}
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                }
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            }
            try {
                val looper = Looper.getMainLooper() ?: Looper.myLooper()
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, lis, looper)
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, lis, looper)
            } catch (_: SecurityException) {}
            catch (_: Exception) {}
            // timeout lepas listener agar tidak bocor (anti FC/leak)
            Thread {
                try { Thread.sleep(45000) } catch (_: Exception) {}
                try { lm.removeUpdates(lis) } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()
        } catch (_: Exception) {}
    }

    /** Hasil khusus panel: link map saja, tanpa embel-embel. */
    fun link(c: Context): String {
        return try {
            val t = lastText(c)
            val i = t.indexOf("http")
            if (i >= 0) t.substring(i).trim().split(" ")[0].trim() else t
        } catch (_: Exception) { "Gagal ambil lokasi." }
    }

    private fun fmt(l: Location): String {
        return try {
            "https://maps.google.com/?q=${l.latitude},${l.longitude} (akurasi ±${l.accuracy.toInt()}m)"
        } catch (_: Exception) { "${l.latitude},${l.longitude}" }
    }
}
