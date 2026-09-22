package com.antimaling.permanen.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(c: Context, i: Intent) {
        super.onEnabled(c, i)
        try { Toast.makeText(c, "Device Admin AKTIF — proteksi permanen on", Toast.LENGTH_LONG).show() } catch (_: Exception) {}
    }
    override fun onDisableRequested(c: Context, i: Intent): CharSequence {
        return "Menonaktifkan akan mematikan anti-maling! Yakin?"
    }
    override fun onDisabled(c: Context, i: Intent) {
        super.onDisabled(c, i)
        try { Toast.makeText(c, "Device Admin MATI — segera aktifkan lagi!", Toast.LENGTH_LONG).show() } catch (_: Exception) {}
    }
}
