package com.antimaling.permanen.lock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antimaling.permanen.R
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.util.Prefs

class LockActivity : AppCompatActivity() {

    private val entered = StringBuilder()

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
            findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this)
            // keypad angka bawaan — anti keyboard tidak muncul
            val keys = mapOf(
                R.id.btnN1 to "1", R.id.btnN2 to "2", R.id.btnN3 to "3",
                R.id.btnN4 to "4", R.id.btnN5 to "5", R.id.btnN6 to "6",
                R.id.btnN7 to "7", R.id.btnN8 to "8", R.id.btnN9 to "9",
                R.id.btnN0 to "0"
            )
            keys.forEach { (id, d) ->
                findViewById<Button>(id)?.setOnClickListener { press(d) }
            }
            findViewById<Button>(R.id.btnDel)?.setOnClickListener {
                try {
                    if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                    renderPin()
                } catch (_: Exception) {}
            }
            findViewById<Button>(R.id.btnClear)?.setOnClickListener {
                try { entered.clear(); renderPin() } catch (_: Exception) {}
            }
            findViewById<Button>(R.id.btnUnlock)?.setOnClickListener { checkPin() }
        } catch (_: Exception) {}
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
                try { Toast.makeText(this, "Dibuka", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                finish()
            } else {
                try { Toast.makeText(this, "PIN salah!", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
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
                findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this)
                CommandHandler.onLockShown(this)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            // jika sudah unlock via SMS, tutup otomatis
            if (!Prefs.isLocked(this)) finish()
            else findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        // Anti-bypass tombol Home: selama masih terkunci, tarik kunci balik ke depan.
        // Dilewati saat ada panggilan aktif agar telepon tetap bisa diangkat.
        try {
            if (!Prefs.isLocked(this) || isFinishing || !callIdle()) return
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (Prefs.isLocked(this@LockActivity) && callIdle()) {
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
}
