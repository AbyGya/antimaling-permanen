# AntiMaling Permanen — APK Anti-Pencurian HP Sendiri

Native Kotlin, **tanpa Firebase**, minim FC (semua API dibungkus try-catch + cek izin + cek null).
Build otomatis via GitHub Actions → ambil APK di tab Actions > Artifacts.

## Fitur
- 🔒 **Lock HP**: overlay full-screen (LockActivity, tampil di atas lockscreen) + `DevicePolicyManager.lockNow()` + teks custom
- 🖼️ **Overlay GUI Home/Lock**: banner merah `TYPE_APPLICATION_OVERLAY`, teks bisa diganti remote `#TEXT#...`
- 📍 **Lacak**: GPS + Network via LocationManager (tanpa Play Services → tidak FC di HP tanpa GMS), balas link Google Maps via SMS
- 🔊 **Dering max volume**: paksa STREAM_ALARM/MUSIC/RING ke max + speaker + getar, tembus mode silent (kecuali DND total)
- 🔦 **Senter kedip-kedip + dering**: `setTorchMode` blink 350ms + alarm bersamaan
- 📩 **Remote SMS** (dari nomor trusted / sertakan PIN):
  ```
  #LOCK            → kunci + overlay ON
  #UNLOCK#1234     → buka (PIN wajib)
  #RING            → dering max + getar
  #STOP#1234       → hentikan semua
  #LOCATE          → balas link maps
  #FLASH#60        → senter kedip 60 dtk + dering
  #TEXT#isi pesan  → ganti teks overlay/lock
  #OVERLAY#ON/OFF  → overlay home/lock
  ```
- 📲 Deteksi ganti SIM → SMS peringatan ke nomor trusted
- 👻 Mode siluman: sembunyikan ikon launcher
- 🔁 Permanen: ForegroundService START_STICKY + BootReceiver + WorkManager 15 mnt + battery-whitelist + Device Admin

## 1. Upload ke GitHub (HP/PC)
```bash
cd antimaling-permanen
git init -b main
git add .
git commit -m "AntiMaling v1"
# buat repo kosong di github.com/new bernama antimaling-permanen (tanpa README)
git remote add origin https://github.com/USERNAME/antimaling-permanen.git
git push -u origin main
```
Lalu buka repo > **Actions** > tunggu hijau > download **AntiMaling-debug** > install di HP.

Atau tanpa git: zip folder ini > upload via web github.com > Add file > Upload files.

## 2. Pasang di HP (wajib 1x)
1. Install APK, buka **AntiMaling**
2. Isi PIN (default `1234`) + nomor trusted + teks overlay → **Simpan**
3. Klik: Aktifkan Device Admin → Izinkan Overlay → Bebas Battery → Minta Izin SMS/Lokasi/Kamera
4. Tes: Kunci, Dering, Senter, Lacak, Overlay ON
5. Opsional: **Sembunyikan Ikon** (buka lagi via Settings > Apps > AntiMaling)

## 3. Tanam permanen via ADB (anti-hapus maksimal)
```bash
adb devices
adb install -r app-debug.apk
# grant sekali klik (tanpa buka HP):
adb shell pm grant com.antimaling.permanen android.permission.READ_SMS
adb shell pm grant com.antimaling.permanen android.permission.RECEIVE_SMS
adb shell pm grant com.antimaling.permanen android.permission.SEND_SMS
adb shell pm grant com.antimaling.permanen android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.antimaling.permanen android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.antimaling.permanen android.permission.CAMERA
adb shell appops set com.antimaling.permanen SYSTEM_ALERT_WINDOW allow
# device owner (HP fresh-reset, belum ada akun Google) = anti-uninstall total:
adb shell dpm set-device-owner com.antimaling.permanen/.receiver.MyAdminReceiver
```
Lihat juga `tanam-permanen.sh`.

> Batasan jujur Android: tanpa root/device-owner, maling masih bisa uninstall via Safe Mode/Factory Reset. Dengan Device Admin + ikon hidden + SIM alert, pencuri awam umumnya gagal. Proteksi 100% butuh root/system-app atau Device Owner.

## ⚠️ Batasan Jujur: HP Dibunuh OEM (Background Kill)

**Ini bukan bug aplikasi, tapi fitur Android (battery optimization per-OEM).**

| Vendor | Gejala | Solusi User (wajib manual) |
|--------|--------|---------------------------|
| **Xiaomi/POCO** | App dimatikan 10-30 menit di background | Security > Battery > AntiMaling > **No restrictions** + App Lock di Recents |
| **Oppo/Realme/OnePlus** | App dibunuh agresif | Settings > Battery > App Battery Management > AntiMaling > **Allow background** + Lock di Recents |
| **Vivo/iQOO** | iManager kill background | iManager > Battery > High background power > AntiMaling > **Allow** |
| **Samsung** | Put to sleep / Deep sleeping | Battery > Background usage limits > **Never sleeping apps** + AntiMaling |
| **Huawei/Honor** | PowerGenie kill | Battery > App launch > AntiMaling > **Manage manually** > allow all |
| **Pixel/Stock Android** | Relatif aman | Battery > App info > Battery > **Unrestricted** |

**Yang dilakukan aplikasi (otomatis):**
- Foreground Service `START_STICKY` + notifikasi permanen
- BootReceiver (auto-start reboot) + `QUICKBOOT_POWERON`
- WorkManager periodic 15 menit (KeepAliveWorker)
- AlarmManager exact 60 detik (restartAlarm) saat service dibunuh
- MQTT heartbeat tiap 20 detik + auto-reconnect
- LWT (Last Will Testament) di broker MQTT → status offline akurat
- Persistent stop flags di SharedPreferences (survive process kill)

**Yang TIDAK bisa diatasi aplikasi (harus user manual):**
- **Force Stop via Settings** → semua process dimatikan paksa oleh sistem (tidak ada app yang kebal)
- **Factory Reset / Safe Mode** → data hilang, app terhapus
- **OEM killer agresif** meski sudah "unrestricted" → beberapa vendor tetap kill setelah beberapa jam

**Solusi 100% (hanya 2 cara):**
1. **Device Owner** (ADB `dpm set-device-owner`) — **wajib HP fresh reset, belum ada akun Google**
2. **Root / Magisk** → install sebagai system app atau gunakan tool seperti `LSPosed` + `AppOpsX`

**Tanpa keduanya di atas, tidak ada aplikasi anti-maling yang 100% kebal dari OEM kill.** Jika HP dicuri dan dicabut baterai/dimatiin paksa → app mati. Fitur SIM-change alert akan kirim SMS ke nomor trusted saat maling ganti kartu.

---

## 4. Anti-FC / No-fungsi dicegah dengan
- minSdk 26, target 34, XML Views (tanpa Compose), deps hanya AndroidX stabil
- Setiap receiver/service/activity = try-catch, cek izin runtime, cek null, cek API level
- Location pakai LocationManager (tidak wajib Play Services)
- MediaPlayer fallback ringtone, torch cek `hasSystemFeature`, SMS `abortBroadcast` aman
- Notification channel selalu dibuat sebelum `startForeground` (wajib Android 8+)
- Foreground type `location|specialUse` + property (wajib Android 14)

## 5. Kontrol dari Laptop (MQTT — tanpa setup ✨)
Tanpa daftar akun, tanpa API key, tanpa Firebase. Buka app 1x → kode 6 digit
muncul otomatis → ketik di panel laptop → online. HP ↔ laptop via broker MQTT
publik (realtime + status online akurat).
Fitur remote: kunci/buka (PIN), dering max, lacak, senter, overlay, teks custom,
**screenshot layar**, **foto kamera depan/belakang**, info HP.
Panel: `cd laptop-panel && python3 server.py`.

## Struktur
```
app/src/main/java/com/antimaling/permanen/
  AntiMalApp.kt, util/Prefs.kt, util/Perms.kt
  receiver/MyAdminReceiver.kt, BootReceiver.kt, SmsReceiver.kt, SimChangeReceiver.kt
  service/GuardService.kt, OverlayService.kt, KeepAliveWorker.kt
  control/CommandHandler.kt, RingManager.kt, FlashManager.kt, LocateManager.kt
  lock/LockActivity.kt, ui/MainActivity.kt
```
