package com.antimaling.permanen.control

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.antimaling.permanen.util.Perms
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object LocateManager {

    private var fused: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null
    private var job: Job? = null

    private fun fused(c: Context): FusedLocationProviderClient {
        return fused ?: LocationServices.getFusedLocationProviderClient(c).also { fused = it }
    }

    /** Link map murni untuk panel (pakai last known cepat). */
    fun link(c: Context): String {
        return try {
            val loc: Location? = Tasks.await(fused(c).lastLocation)
            if (loc != null) fmt(loc) else fallbackLast(c)
        } catch (_: Exception) { fallbackLast(c) }
    }

    private fun fallbackLast(c: Context): String {
        return try {
            if (!Perms.location(c)) return "AntiMaling: izin lokasi belum diberikan."
            val lm = c.getSystemService(android.location.LocationManager::class.java) ?: return "AntiMaling: GPS tidak tersedia."
            var loc = try { lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
            if (loc == null) loc = try { lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
            if (loc == null) loc = try { lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }
            if (loc == null) "AntiMaling: lokasi belum siap, coba lagi sebentar." else fmt(loc)
        } catch (e: SecurityException) { "AntiMaling: izin lokasi ditolak." }
        catch (_: Exception) { "AntiMaling: gagal ambil lokasi." }
    }

    /** Lokasi fresh high-accuracy untuk panel/SMS (pakai FusedLocationProviderClient). */
    @SuppressLint("MissingPermission")
    fun requestAndReply(c: Context, to: String?) {
        try {
            if (!Perms.location(c)) {
                CommandHandler.reply(c, to, "AntiMaling: izin lokasi belum diberikan."); return
            }
            val f = fused(c)
            // last known dulu (cepat)
            val lastLoc: Location? = try { Tasks.await(f.lastLocation) } catch (_: Exception) { null }
            if (lastLoc != null) CommandHandler.reply(c, to, fmt(lastLoc))
            // request fresh high accuracy
            val req = LocationRequest.create().apply {
                priority = Priority.PRIORITY_HIGH_ACCURACY
                interval = 5000; fastestInterval = 3000; maxWaitTime = 10000
            }
            callback = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    r.locations.firstOrNull()?.let { loc ->
                        try { CommandHandler.reply(c, to, "AntiMaling UPDATE: " + fmt(loc)) } catch (_: Exception) {}
                        try { f.removeLocationUpdates(this) } catch (_: Exception) {}
                    }
                }
            }
            f.requestLocationUpdates(req, callback!!, Looper.getMainLooper())
            // timeout 60 dtk
            CoroutineScope(Dispatchers.IO).launch {
                try { kotlinx.coroutines.delay(60_000) } catch (_: Exception) {}
                try { f.removeLocationUpdates(callback!!) } catch (_: Exception) {}
            }
        } catch (e: SecurityException) { CommandHandler.reply(c, to, "AntiMaling: izin lokasi ditolak.") }
        catch (_: Exception) { CommandHandler.reply(c, to, "AntiMaling: gagal ambil lokasi.") }
    }

    private fun fmt(l: Location): String {
        return try {
            "https://maps.google.com/?q=${l.latitude},${l.longitude} (akurasi ±${l.accuracy.toInt()}m)"
        } catch (_: Exception) { "${l.latitude},${l.longitude}" }
    }
}