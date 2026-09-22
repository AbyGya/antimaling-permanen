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

## 4. Anti-FC / No-fungsi dicegah dengan
- minSdk 26, target 34, XML Views (tanpa Compose), deps hanya AndroidX stabil
- Setiap receiver/service/activity = try-catch, cek izin runtime, cek null, cek API level
- Location pakai LocationManager (tidak wajib Play Services)
- MediaPlayer fallback ringtone, torch cek `hasSystemFeature`, SMS `abortBroadcast` aman
- Notification channel selalu dibuat sebelum `startForeground` (wajib Android 8+)
- Foreground type `location|specialUse` + property (wajib Android 14)

## Struktur
```
app/src/main/java/com/antimaling/permanen/
  AntiMalApp.kt, util/Prefs.kt, util/Perms.kt
  receiver/MyAdminReceiver.kt, BootReceiver.kt, SmsReceiver.kt, SimChangeReceiver.kt
  service/GuardService.kt, OverlayService.kt, KeepAliveWorker.kt
  control/CommandHandler.kt, RingManager.kt, FlashManager.kt, LocateManager.kt
  lock/LockActivity.kt, ui/MainActivity.kt
```
