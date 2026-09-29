package com.antimaling.permanen.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antimaling.permanen.R
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.util.Prefs

class LockActivity : AppCompatActivity() {

    private val entered = StringBuilder()
    private var lastRelaunch = 0L

    /** Unlock jarak jauh (SMS/panel): tutup layar bila status sudah terbuka. */
    private val unlockRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent?) {
            try { if (!Prefs.isLocked(this@LockActivity)) finish() } catch (_: Exception) {}
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {}
            // keyboard sistem tidak dipakai (pakai keypad bawaan) — cegah lag IME
            try { window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) } catch (_: Exception) {}
        } catch (_: Exception) {}
        setContentView(R.layout.activity_lock)
        try { CommandHandler.onLockShown(this) } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockRx, IntentFilter(CommandHandler.UNLOCK_ACTION), Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(unlockRx, IntentFilter(CommandHandler.UNLOCK_ACTION))
        } catch (_: Exception) {}
        try { refreshText() } catch (_: Exception) {}
        loadEvidence()
    }

    private fun refreshText() {
        try { findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this) } catch (_: Exception) {}
    }

    /**
     * Muat foto pencuri + lokasi ke layar kunci.
     * Semua kerja berat (decode bitmap, ambil lokasi) jalan di thread
     * background supaya keypad tidak pernah lag — keypad yang tidak
     * bisa dipencet adalah bug yang pernah terjadi sebelumnya.
     */
    private fun loadEvidence() {
        val box = try { findViewById<android.view.View>(R.id.llEvidence) } catch (_: Exception) { null } ?: return
        try {
            val warn = try { Prefs.getStr(this, "warn_text", "") } catch (_: Exception) { "" }
            if (warn.isNotBlank()) findViewById<TextView>(R.id.tvWarn)?.text = warn
        } catch (_: Exception) {}

        if (!try { com.antimaling.permanen.util.MalingPhoto.exists(this) } catch (_: Exception) { false }) {
            // tidak ada foto -> tetap tampilkan peringatan + lokasi saja
            try { box.visibility = android.view.View.VISIBLE } catch (_: Exception) {}
            loadFix()
            return
        }

        try { box.visibility = android.view.View.VISIBLE } catch (_: Exception) {}
        Thread {
            val bmp = try { com.antimaling.permanen.util.MalingPhoto.load(this) } catch (_: Exception) { null }
            if (bmp != null) {
                try { runOnUiThread {
                    findViewById<android.widget.ImageView>(R.id.imgMaling)?.setImageBitmap(bmp)
                } } catch (_: Exception) {}
            }
            loadFix()
        }.apply { isDaemon = true; name = "lock-evidence"; start() }
    }

    /** Ambil lokasi di background lalu tulis ke layar (tidak boleh di thread UI). */
    private fun loadFix() {
        Thread {
            val f = try { com.antimaling.permanen.control.LocateManager.snapshot(this) } catch (_: Exception) { null }
            val txt = if (f == null) "Lokasi belum tersedia — pemilik sedang mencoba."
            else "📍 ${"%.5f".format(f.lat)}, ${"%.5f".format(f.lon)}  (±${f.acc.toInt()}m)\n${f.url}"
            try { runOnUiThread {
                findViewById<TextView>(R.id.tvFix)?.text = txt
            } } catch (_: Exception) {}
        }.apply { isDaemon = true; name = "lock-fix"; start() }
    }

    /**
     * Dipanggil dari android:onClick di layout (tag berisi digit / "del" / "clear").
     * Sengaja TIDAK memakai findViewById + setOnClickListener: kalau salah satu
     * melempar exception, seluruh keypad ikut mati. Dipasang oleh Android saat inflate.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onKeypadClick(v: View) {
        val key = try { v.tag?.toString() ?: return } catch (_: Exception) { return }
        // umpan balik: agar pengguna pasti tombolnya terdaftar
        try { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) {}
        try {
            v.alpha = 0.5f
            v.postDelayed({ try { v.alpha = 1f } catch (_: Exception) {} }, 90)
        } catch (_: Exception) {}

        when (key) {
            "del" -> { if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1); renderPin() }
            "clear" -> { entered.clear(); renderPin() }
            else -> press(key)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUnlockClick(v: View) {
        try { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) {}
        checkPin()
    }

    private fun renderPin() {
        try { findViewById<EditText>(R.id.etUnlockPin)?.setText(entered.toString()) } catch (_: Exception) {}
    }

    private fun press(d: String) {
        try {
            if (entered.length >= 12) return
            entered.append(d)
            renderPin()
            // verifikasi otomatis saat panjang cocok — tanpa tombol
            val pin = Prefs.getPin(this)
            if (entered.length == pin.length) checkPin()
        } catch (_: Exception) {}
    }

    private fun checkPin() {
        try {
            if (entered.toString() == Prefs.getPin(this)) {
                CommandHandler.unlock(this)
                // pemilik sah berhasil buka -> reset semua counter jebakan
                try { com.antimaling.permanen.control.TheftGuard.clear(this) } catch (_: Exception) {}
                try { Toast.makeText(this, "Dibuka", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                finish()
            } else {
                val n = try { com.antimaling.permanen.control.TheftGuard.onWrongPin(this) } catch (_: Exception) { 0 }
                val msg = if (n >= com.antimaling.permanen.control.TheftGuard.PIN_THRESHOLD)
                    "PIN salah! ($n×) — alarm & foto aktif"
                else "PIN salah! ($n/${com.antimaling.permanen.control.TheftGuard.PIN_THRESHOLD})"
                try { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } catch (_: Exception) {}
                entered.clear()
                renderPin()
            }
        } catch (_: Exception) {}
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        try {
            if (!Prefs.isLocked(this)) finish()
            else {
                refreshText()
                CommandHandler.onLockShown(this)
                loadEvidence()
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        visible = true
        try {
            // jika sudah unlock via SMS, tutup otomatis
            if (!Prefs.isLocked(this)) finish()
            else refreshText()
        } catch (_: Exception) {}
    }

    override fun onStop() {
        visible = false
        try { super.onStop() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        visible = false
        try { unregisterReceiver(unlockRx) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // Anti-bypass tombol Home: selama masih terkunci, tarik kunci balik ke depan.
        // Dilewati saat ada panggilan aktif agar telepon tetap bisa diangkat.
        //
        // PENTING: relaunch dibatasi (cooldown + flag), karena setiap startActivity
        // membuat activity pause-resume, dan selama transisi itu window-nya tidak
        // menerima sentuhan -> tombol keypad Professionals "tidak bisa dipencet".
        try {
            if (!Prefs.isLocked(this) || isFinishing || !callIdle()) return
            val now = System.currentTimeMillis()
            if (now - lastRelaunch < RELAUNCH_COOLDOWN) return
            lastRelaunch = now
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (Prefs.isLocked(this@LockActivity) && callIdle() && !isFinishing) {
                        startActivity(Intent(this, LockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                    }
                } catch (_: Exception) {}
            }, 800)
        } catch (_: Exception) {}
    }

    private fun callIdle(): Boolean {
        return try {
            val tm = getSystemService(TelephonyManager::class.java) ?: return true
            tm.callState == TelephonyManager.CALL_STATE_IDLE
        } catch (_: SecurityException) { true }
        catch (_: Exception) { true }
    }

    @Deprecated("back blocked")
    override fun onBackPressed() {
        // blokir tombol back saat terkunci — anti bypass
        try {
            if (Prefs.isLocked(this)) return
            super.onBackPressed()
        } catch (_: Exception) {}
    }

    companion object {
        /** True saat layar kunci benar-benar sedang tampil & fokus. */
        @Volatile var visible = false
            private set

        // PENTING: cooldown hanya menahan relaunch BERUNTUN (sistem yang menembak
        // ulang tiap 60 dtk). Tidak discourage tekan Home asli dari pemilik —
        // kalau cooldown ikut di-reset di onResume, celah bypass-nya terbuka.
        private const val RELAUNCH_COOLDOWN = 1200L

        /** Dipakai GuardService: jangan tembak ulang kalau sudah tampil. */
        fun isShowing(): Boolean = visible

        /** Tampilkan layar kunci. Aman dipanggil berulang. */
        fun show(c: Context) {
            try {
                if (visible) return
                c.startActivity(Intent(c, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            } catch (_: Exception) {}
        }
    }
}
