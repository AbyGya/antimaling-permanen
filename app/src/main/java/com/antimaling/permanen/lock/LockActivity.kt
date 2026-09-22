package com.antimaling.permanen.lock

import android.os.Build
import android.os.Bundle
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

    override fun onResume() {
        super.onResume()
        try {
            // jika sudah unlock via SMS, tutup otomatis
            if (!Prefs.isLocked(this)) finish()
            else findViewById<TextView>(R.id.tvLockText)?.text = Prefs.getText(this)
        } catch (_: Exception) {}
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
