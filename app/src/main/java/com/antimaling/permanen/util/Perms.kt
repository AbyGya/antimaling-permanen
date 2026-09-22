package com.antimaling.permanen.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun has(c: Context, perm: String): Boolean = try {
        ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun sms(c: Context) = has(c, Manifest.permission.RECEIVE_SMS)
            && has(c, Manifest.permission.READ_SMS)
            && has(c, Manifest.permission.SEND_SMS)

    fun location(c: Context): Boolean {
        val fine = has(c, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(c, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine || coarse
    }

    fun camera(c: Context) = has(c, Manifest.permission.CAMERA)

    fun notif(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return has(c, Manifest.permission.POST_NOTIFICATIONS)
    }

    fun needed(): Array<String> {
        val l = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }
}
