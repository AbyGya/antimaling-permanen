package com.antimaling.permanen.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.antimaling.permanen.R
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.control.FlashManager
import com.antimaling.permanen.control.LocateManager
import com.antimaling.permanen.control.RingManager
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.receiver.MyAdminReceiver
import com.antimaling.permanen.service.GuardService
import com.antimaling.permanen.service.OverlayService
import com.antimaling.permanen.util.Perms
import com.antimaling.permanen.util.Prefs

class MainActivity : AppCompatActivity() {

    private fun toast(s: String) { try { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() } catch (_: Exception) {} }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        try { GuardService.start(this) } catch (_: Exception) {}

        val etPin = findViewById<EditText>(R.id.etPin)
        val etTrusted = findViewById<EditText>(R.id.etTrusted)
        val etText = findViewById<EditText>(R.id.etCustomText)
        try {
            etPin.setText(Prefs.getPin(this))
            etTrusted.setText(Prefs.getTrusted(this))
            etText.setText(Prefs.getText(this))
        } catch (_: Exception) {}

        findViewById<Button>(R.id.btnSave)?.setOnClickListener {
            try {
                val pin = etPin.text.toString().trim().ifBlank { "1234" }
                Prefs.setPin(this, pin)
                Prefs.setTrusted(this, etTrusted.text.toString().trim())
                Prefs.setText(this, etText.text.toString().trim().ifBlank { getString(R.string.default_lock_text) })
                OverlayService.restart(this)
                toast("Tersimpan!")
                refresh()
            } catch (_: Exception) { toast("Gagal simpan") }
        }

        findViewById<Button>(R.id.btnAdmin)?.setOnClickListener { requestAdmin() }
        findViewById<Button>(R.id.btnOverlayPerm)?.setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnBattery)?.setOnClickListener { requestBattery() }
        findViewById<Button>(R.id.btnPerms)?.setOnClickListener {
            try { ActivityCompat.requestPermissions(this, Perms.needed(), 11) } catch (_: Exception) {}
        }

        findViewById<Button>(R.id.btnTestLock)?.setOnClickListener {
            CommandHandler.lock(this)
            try { startActivity(Intent(this, LockActivity::class.java)) } catch (_: Exception) {}
        }
        findViewById<Button>(R.id.btnTestRing)?.setOnClickListener { RingManager.start(this); toast("Dering MAX... tekan STOP untuk henti") }
        findViewById<Button>(R.id.btnTestFlash)?.setOnClickListener { FlashManager.start(this, 30); RingManager.start(this); toast("Senter kedip + dering 30 dtk") }
        findViewById<Button>(R.id.btnLocate)?.setOnClickListener { toast(LocateManager.lastText(this)) }
        findViewById<Button>(R.id.btnOverlayOn)?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { requestOverlay(); return@setOnClickListener }
            Prefs.setOverlay(this, true); OverlayService.restart(this); toast("Overlay ON"); refresh()
        }
        findViewById<Button>(R.id.btnOverlayOff)?.setOnClickListener {
            Prefs.setOverlay(this, false)
            try { stopService(Intent(this, OverlayService::class.java)) } catch (_: Exception) {}
            toast("Overlay OFF"); refresh()
        }
        findViewById<Button>(R.id.btnStopAll)?.setOnClickListener { CommandHandler.stopAll(this); toast("Semua alarm STOP"); refresh() }
        findViewById<Button>(R.id.btnHideIcon)?.setOnClickListener { setIcon(false) }
        findViewById<Button>(R.id.btnShowIcon)?.setOnClickListener { setIcon(true) }

        refresh()
    }

    override fun onResume() { super.onResume(); try { refresh() } catch (_: Exception) {} }

    private fun refresh() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val cn = ComponentName(this, MyAdminReceiver::class.java)
            val admin = try { dpm?.isAdminActive(cn) == true } catch (_: Exception) { false }
            val overlay = try { Settings.canDrawOverlays(this) } catch (_: Exception) { false }
            val pm = getSystemService(PowerManager::class.java)
            val batt = try { pm?.isIgnoringBatteryOptimizations(packageName) == true } catch (_: Exception) { false }
            val t = "Status:\n• Admin: ${if (admin) "AKTIF ✅" else "MATI ❌"}\n" +
                    "• Overlay: ${if (overlay) "OK ✅" else "BELUM ❌"}\n" +
                    "• Battery bebas: ${if (batt) "OK ✅" else "BELUM ❌"}\n" +
                    "• SMS: ${if (Perms.sms(this)) "OK ✅" else "BELUM ❌"} | " +
                    "Lokasi: ${if (Perms.location(this)) "OK ✅" else "BELUM ❌"}\n" +
                    "• Terkunci: ${if (Prefs.isLocked(this)) "YA 🔒" else "tidak"} | Dering: ${if (Prefs.isRinging(this)) "YA 🔊" else "tidak"}"
            findViewById<TextView>(R.id.tvStatus)?.text = t
        } catch (_: Exception) {}
    }

    private fun requestAdmin() {
        try {
            val cn = ComponentName(this, MyAdminReceiver::class.java)
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_desc))
            }
            startActivityForResult(i, 21)
        } catch (_: Exception) { toast("Gagal buka admin") }
    }

    private fun requestOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                toast("Aktifkan 'Tampilkan di atas aplikasi lain'")
            } else toast("Overlay sudah OK")
        } catch (_: Exception) {}
    }

    private fun requestBattery() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm?.isIgnoringBatteryOptimizations(packageName) == true) { toast("Sudah bebas battery"); return }
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun setIcon(show: Boolean) {
        try {
            // Alias adalah ikon launcher utama — sembunyikan untuk mode siluman permanen
            val alias = ComponentName(this, "com.antimaling.permanen.LauncherAlias")
            packageManager.setComponentEnabledSetting(
                alias,
                if (show) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            toast(if (show) "Ikon ditampilkan" else "Ikon disembunyikan! Buka via SMS / Settings > Apps")
            if (!show) toast("CATAT: buka lagi via Settings > Apps > AntiMaling")
        } catch (_: Exception) { toast("Gagal ubah ikon") }
    }

    @Deprecated("result")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == 21) { toast(if (res == RESULT_OK) "Admin AKTIF" else "Admin batal"); refresh() }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        refresh()
    }

    companion object {
        fun open(c: Context) { try { c.startActivity(Intent(c, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }
    }
}
