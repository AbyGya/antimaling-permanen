package com.antimaling.permanen.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.antimaling.permanen.R
import com.antimaling.permanen.control.CommandHandler
import com.antimaling.permanen.control.FlashManager
import com.antimaling.permanen.control.LocateManager
import com.antimaling.permanen.control.RingManager
import com.antimaling.permanen.lock.LockActivity
import com.antimaling.permanen.net.MqttLink
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
        findViewById<Button>(R.id.btnPerms)?.setOnClickListener { askPermissions() }

        findViewById<Button>(R.id.btnTestLock)?.setOnClickListener {
            CommandHandler.lock(this)
            try { startActivity(Intent(this, LockActivity::class.java)) } catch (_: Exception) {}
        }
        findViewById<Button>(R.id.btnTestRing)?.setOnClickListener { RingManager.start(this); toast("Dering MAX... tekan STOP untuk henti") }
        findViewById<Button>(R.id.btnTestFlash)?.setOnClickListener { FlashManager.start(this, 30); RingManager.start(this); toast("Senter kedip + dering 30 dtk") }
        findViewById<Button>(R.id.btnLocate)?.setOnClickListener { toast(LocateManager.link(this)) }
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
        findViewById<Button>(R.id.btnClearAudit)?.setOnClickListener {
            try { Prefs.putStr(this, "audit", ""); toast("Riwayat dibersihkan"); refresh() } catch (_: Exception) {}
        }
        findViewById<Button>(R.id.btnRefreshHealth)?.setOnClickListener {
            try {
                // paksa bangunkan semua jaring pengaman, lalu tampilkan hasilnya
                GuardService.start(this)
                com.antimaling.permanen.service.KeepAlive.scheduleJob(this)
                com.antimaling.permanen.service.KeepAlive.scheduleWorker(this)
                MqttLink.start(this)
            } catch (_: Exception) {}
            toast("Membangunkan service… buka 2 detik lagi")
            Handler(Looper.getMainLooper()).postDelayed({ try { refresh() } catch (_: Exception) {} }, 2000)
        }

        // Tombol diagnosis: membaca alasan kematian process langsung di HP,
        // tanpa perlu komputer tersambung.
        findViewById<Button>(R.id.btnDiag)?.setOnClickListener {
            try {
                val s = StringBuilder("Riwayat kematian process:\n\n")
                val list = com.antimaling.permanen.util.ExitInfo.reports(this, 8)
                if (list.isEmpty()) s.append("Tidak ada — app belum pernah dibunuh.\n")
                list.forEach { e ->
                    s.append("• ").append(e.reason).append("\n   ").append(e.at)
                    if (e.detail.isNotBlank()) s.append("\n   ").append(e.detail.take(160))
                    s.append("\n\n")
                }
                s.append("Kirim teks ini ke saya kalau perlu.\n\n")
                s.append("USER_REQUESTED / USER_STOPPED → kamu atau XOS yang")
                s.append("\n   menghentikan; perlu whitelist 8 langkah XOS.")
                s.append("\nCRASH → bug di app, bisa saya perbaiki.")
                s.append("\nLOW_MEMORY → XOS kehabisan RAM.")
                s.append("\nOTHER / SIGNALED → cleaner XOS yang membunuhnya.")
                AlertDialog.Builder(this).setTitle("Diagnosis Service")
                    .setMessage(s.toString())
                    .setPositiveButton("Tutup", null).show()
            } catch (_: Exception) { toast("Gagal baca diagnosis") }
        }
        findViewById<Button>(R.id.btnHideIcon)?.setOnClickListener { setIcon(false) }
        findViewById<Button>(R.id.btnShowIcon)?.setOnClickListener { setIcon(true) }

        // ---- Link laptop via MQTT (nol setup: kode dibuat otomatis) ----
        try {
            MqttLink.ensurePair(this)
            GuardService.start(this)
            MqttLink.start(this)
        } catch (_: Exception) {}
        findViewById<Button>(R.id.btnShotConsent)?.setOnClickListener { ShotConsentActivity.open(this) }
        findViewById<Button>(R.id.btnCloudTest)?.setOnClickListener {
            toast("Menghubungkan...")
            Thread {
                val msg = try { MqttLink.test(this) } catch (e: Exception) { "Error: ${e.message}" }
                runOnUiThread { toast(msg); try { refresh() } catch (_: Exception) {} }
            }.apply { isDaemon = true }.start()
        }
        findViewById<Button>(R.id.btnPairNew)?.setOnClickListener {
            try {
                // kode override manual (8 karakter). Kalau dikosongkan -> kembali
                // ke kode stabil otomatis yang diturunkan dari ID perangkat.
                val fresh = com.antimaling.permanen.util.CodeGen.random()
                Prefs.setPair(this, fresh)
                MqttLink.reconnect(this)
                toast("Kode baru: $fresh — ketik di panel laptop")
                refresh()
            } catch (_: Exception) { toast("Gagal ganti kode") }
        }

        // ---- Izin Android: diminta sekali, saat pertama kali app dibuka ----
        firstRunPermissions()

        refresh()
    }

    /**
     * Saat pertama dijalankan setelah install: jelaskan dulu apa yang dibutuhkan,
     * lalu system dialog Android muncul. Ini satu-satunya saat app meminta izin —
     * sesudahnya pemilik yang pegang kendali, dan bisa mencabut kapan saja di
     * Settings > Apps > AntiMaling > Permissions.
     */
    private fun firstRunPermissions() {
        try {
            if (Prefs.getBool(this, "first_run_done", false)) return
            Prefs.putBool(this, "first_run_done", true)
            val missing = Perms.needed().filter { !Perms.has(this, it) }
            if (missing.isEmpty()) return

            AlertDialog.Builder(this)
                .setTitle("Izin yang dibutuhkan")
                .setMessage(
                    "AntiMaling perlu izin berikut agar proteksi bisa bekerja:\n\n" +
                        "📍 Lokasi — melacak HP kalau dicuri\n" +
                        "📷 Kamera — bukti foto pencuri\n" +
                        "📨 SMS — peringatan kalau SIM diganti, & remote via SMS\n" +
                        "🔔 Notifikasi — status proteksi & alarm\n\n" +
                        "Semua izin bisa kamu cabut kapan saja di:\n" +
                        "Settings > Apps > AntiMaling > Permissions"
                )
                .setPositiveButton("Beri Izin") { _, _ -> askPermissions() }
                .setNegativeButton("Nanti", null)
                .show()
        } catch (_: Exception) {}
    }

    private fun askPermissions() {
        try { ActivityCompat.requestPermissions(this, Perms.needed(), 11) } catch (_: Exception) {}
    }

    /** Kalau ada yang ditolak permanen, arahkan ke Settings (Android tak izinkan minta ulang). */
    private fun explainMissing() {
        try {
            val miss = Perms.needed().filter { !Perms.has(this, it) }
            if (miss.isEmpty()) return
            AlertDialog.Builder(this)
                .setTitle("Sebagian izin belum diberikan")
                .setMessage(
                    "Masih ada ${miss.size} izin yang kosong.\n\n" +
                        "Kalau tombol \"Allow\" tidak muncul, buka manual:\n" +
                        "Settings > Apps > AntiMaling > Permissions\n\n" +
                        "Tanpa izin lokasi/kamera, fitur bukti & lacak tidak akan bekerja."
                )
                .setPositiveButton("Buka Settings") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("Tutup", null)
                .show()
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try { refresh() } catch (_: Exception) {}
        // tiap buka app: pastikan link laptop nyambung + cek flag stop persisten
        try { RingManager.checkStopFlag(this) } catch (_: Exception) {}
        try { FlashManager.checkStopFlag(this) } catch (_: Exception) {}
        try { MqttLink.start(this) } catch (_: Exception) {}
    }

    private fun refresh() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val cn = ComponentName(this, MyAdminReceiver::class.java)
            val admin = try { dpm?.isAdminActive(cn) == true } catch (_: Exception) { false }
            val overlay = try { Settings.canDrawOverlays(this) } catch (_: Exception) { false }
            val pm = getSystemService(PowerManager::class.java)
            val batt = try { pm?.isIgnoringBatteryOptimizations(packageName) == true } catch (_: Exception) { false }
            val ver = try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                "v${pi.versionName}"
            } catch (_: Exception) { "" }
            val t = "Status $ver:\n• Admin: ${if (admin) "AKTIF ✅" else "MATI ❌"}\n" +
                    "• Overlay: ${if (overlay) "OK ✅" else "BELUM ❌"}\n" +
                    "• Battery bebas: ${if (batt) "OK ✅" else "BELUM ❌"}\n" +
                    "• SMS: ${if (Perms.sms(this)) "OK ✅" else "BELUM ❌"} | " +
                    "Lokasi: ${if (Perms.location(this)) "OK ✅" else "BELUM ❌"}\n" +
                    "• Link laptop: ${if (MqttLink.connected()) "ON ✅" else "menghubungkan..."} | " +
                    "Shot: ${if (com.antimaling.permanen.spy.ShotTaker.hasConsent()) "siap ✅" else "butuh izin ❌"}\n" +
                    "• Sync terakhir: ${Prefs.getLastSync(this)}\n" +
                    "• Terkunci: ${if (Prefs.isLocked(this)) "YA 🔒" else "tidak"} | Dering: ${if (Prefs.isRinging(this)) "YA 🔊" else "tidak"}"
            findViewById<TextView>(R.id.tvStatus)?.text = t
            try {
                val pair = Prefs.resolveCode(this)
                findViewById<TextView>(R.id.tvPair)?.text =
                    if (pair.isBlank()) "••••-••••" else pair.chunked(4).joinToString("-")
                val link = if (MqttLink.connected()) "Link laptop: ONLINE ✅ — ketik kode di atas pada panel"
                else "Link laptop: menghubungkan... (${Prefs.getLastSync(this)})"
                findViewById<TextView>(R.id.tvMqtt)?.text = link
            } catch (_: Exception) {}

            // riwayat perintah dari panel
            try {
                val log = Prefs.auditLog(this).take(12)
                findViewById<TextView>(R.id.tvAudit)?.text =
                    if (log.isEmpty()) "Belum ada perintah dari panel."
                    else log.joinToString("\n")
            } catch (_: Exception) {}
            refreshHealth()
        } catch (_: Exception) {}
    }

    /**
     * Panel kesehatan: menampilkan bukti service benar-benar hidup atau tidak.
     * Tujuannya supaya pengguna tidak perlu menebak kenapa mati.
     */
    private fun refreshHealth() {
        try {
            val yn = { b: Boolean -> if (b) "YA" else "TIDAK" }
            val admin = try {
                val dpm = getSystemService(DevicePolicyManager::class.java)
                dpm?.isAdminActive(ComponentName(this, MyAdminReceiver::class.java)) == true
            } catch (_: Exception) { false }
            val overlay = try { Settings.canDrawOverlays(this) } catch (_: Exception) { false }
            val batt = try {
                getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(packageName) == true
            } catch (_: Exception) { false }
            val camErr = try { Prefs.getCamError(this) } catch (_: Exception) { "" }
            val ovlErr = try { com.antimaling.permanen.service.OverlayService.lastStartError } catch (_: Exception) { "" }
            val api = try { android.os.Build.VERSION.SDK_INT } catch (_: Exception) { 0 }
            val exits = try { com.antimaling.permanen.util.ExitInfo.reports(this, 4) } catch (_: Exception) { emptyList<com.antimaling.permanen.util.ExitInfo.Report>() }
            val s = buildString {
                append("Android API   : ").append(api).append(if (api >= 36) "  (XOS 16 - ketat)" else "").append('\n')

                // INI YANG PALING PENTING: alasan sebenarnya kenapa process dibunuh
                append("\n── Kenapa process mati ──\n")
                if (exits.isEmpty()) {
                    append("Belum ada riwayat (app belum pernah dibunuh).\n")
                } else {
                    exits.forEach { e ->
                        append("• ").append(e.reason).append('\n')
                        append("   ").append(e.at).append('\n')
                    }
                }
                append("\n")

                append("Service hidup : ").append(Prefs.getSvcStart(this@MainActivity)).append('\n')
                append("Foreground     : ").append(Prefs.getFgStatus(this@MainActivity)).append('\n')
                append("Heartbeat      : ").append(Prefs.getBeat(this@MainActivity)).append('\n')
                append("Alarm 60 dtk   : ").append(yn(Prefs.getAlarmArmed(this@MainActivity))).append('\n')
                append("JobScheduler   : ").append(yn(Prefs.getJobArmed(this@MainActivity))).append('\n')
                append("Device Admin   : ").append(yn(admin)).append('\n')
                append("Overlay izin   : ").append(yn(overlay)).append('\n')
                append("Bebas battery  : ").append(yn(batt))
                if (ovlErr.isNotBlank()) {
                    append("\nOverlay start  : DITOLAK (Android 16)")
                    append("\n                 dicoba ulang tiap 15 dtk")
                }
                if (camErr.isNotBlank() && camErr != "-") {
                    append("\nKamera         : ").append(camErr)
                    append("\nFoto terakhir  : ").append(Prefs.getLastPhoto(this@MainActivity))
                }
            }
            findViewById<TextView>(R.id.tvHealth)?.text = s
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
        try {
            if (rc == 11) {
                val denied = r.count { it != PackageManager.PERMISSION_GRANTED }
                if (denied > 0) explainMissing()
            }
        } catch (_: Exception) {}
        refresh()
    }

    companion object {
        fun open(c: Context) { try { c.startActivity(Intent(c, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }
    }
}
