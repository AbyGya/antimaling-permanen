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
        } catch (_: Exception) {}
        setContentView(R.layout.activity_lock)
        try { CommandHandler.onLockShown(this) } catch (_: Exception) {}
        try {
            findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this)
            findViewById<Button>(R.id.btnUnlock)?.setOnClickListener {
                try {
                    val pin = findViewById<EditText>(R.id.etUnlockPin)?.text?.toString()?.trim() ?: ""
                    if (pin == Prefs.getPin(this)) {
                        CommandHandler.unlock(this)
                        try { Toast.makeText(this, "Dibuka", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                        finish()
                    } else {
                        try { Toast.makeText(this, "PIN salah!", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
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
