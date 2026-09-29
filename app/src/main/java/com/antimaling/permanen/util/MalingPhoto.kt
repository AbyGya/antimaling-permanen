package com.antimaling.permanen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/**
 * Foto "maling" yang ditampilkan di layar kunci.
 *
 * Disimpan di internal storage (bukan SharedPreferences) karena isinya binary
 * dan bisa berukuran ratusan KB. Internal storage bertahan setelah reboot dan
 * setelah app di-clear cache — hanya hilang kalau app di-uninstall atau HP di-reset.
 */
object MalingPhoto {

    private const val NAME = "maling.jpg"
    private const val MAX_W = 900

    fun file(c: Context): File = File(c.filesDir, NAME)

    fun exists(c: Context): Boolean = try { file(c).exists() && file(c).length() > 0 } catch (_: Exception) { false }

    fun delete(c: Context): Boolean = try { file(c).delete() } catch (_: Exception) { false }

    /**
     * Simpan foto dari base64 (dikirim panel). Dimensi dibatasi supaya tidak
     * boros storage dan tidak lambat di-decode saat lockscreen dibuka.
     * Return pesan error, string kosong = sukses.
     */
    fun saveFromBase64(c: Context, b64: String): String {
        return try {
            if (b64.isBlank()) return "Data foto kosong."
            val raw = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { return "Foto tidak valid (bukan base64)." }
            if (raw.isEmpty() || raw.size < 100) return "Foto terlalu kecil / rusak."
            if (raw.size > 6 * 1024 * 1024) return "Foto terlalu besar (maks 6MB)."

            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return "Foto tidak bisa dibaca (format rusak?)."
            val w = bmp.width.coerceAtLeast(1)
            val out = if (w > MAX_W) {
                val s = Bitmap.createScaledBitmap(bmp, MAX_W, (bmp.height * (MAX_W.toFloat() / w)).toInt().coerceAtLeast(1), true)
                try { if (s != bmp) bmp.recycle() } catch (_: Exception) {}
                s
            } else bmp
            val fos = FileOutputStream(file(c))
            try { out.compress(Bitmap.CompressFormat.JPEG, 80, fos) } finally { try { fos.close() } catch (_: Exception) {} }
            try { out.recycle() } catch (_: Exception) {}
            ""
        } catch (e: Exception) {
            "Gagal menyimpan: ${e.message}"
        }
    }

    /** Bitmap siap tampil, atau null. Dipanggil di thread background. */
    fun load(c: Context): Bitmap? {
        return try {
            val f = file(c)
            if (!f.exists()) return null
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (_: Exception) { null }
    }
}
