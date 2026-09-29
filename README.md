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
2. Muncul dialog penjelasan → **Beri Izin** (satu-satunya kali app minta izin).
   Android akan menampilkan dialog resmi untuk Lokasi / Kamera / SMS / Notifikasi.
   Semua izin bisa dicabut kapan saja: *Settings > Apps > AntiMaling > Permissions*
3. Isi PIN (default `1234`) + nomor trusted + teks overlay → **Simpan**
4. Klik: Aktifkan Device Admin → Izinkan Overlay → Bebas Battery
5. **WAJIB: lakukan 8 langkah whitelist XOS 15** (bagian 3b) — tanpa ini app
   akan dibunuh XOS di background
6. Tes: Kunci, Dering, Senter, Lacak, Overlay ON
7. Cek bagian **6. Kesehatan Service** — semua harus `YA` / `OK`
8. Opsional: **Sembunyikan Ikon** (buka lagi via Settings > Apps > AntiMaling)

### Jejak audit
Section **6. Riwayat Kontrol dari Laptop** di app berisi daftar lengkap perintah
yang pernah masuk dari panel (kunci, dering, foto, lokasi, dll) beserta waktunya.
Jadi selalu bisa dicek sendiri apa saja yang sudah dikontrol — bukan sekadar
dengan percaya begitu saja.

## 2b. Panel laptop
```bash
cd laptop-panel
python3 server.py       # buka http://127.0.0.1:8765/
```
Ketik kode yang tampil di app HP (format `XXXX-XXXX`). Kode ini **tidak berubah**
walau kamu menghapus data app, karena diturunkan dari ID perangkat — jadi tidak
perlu pairing ulang setelah Clear storage.

Fitur panel: peta langsung + jejak perjalanan, galeri foto/screenshot, tombol
**Bukti Pencurian** (lokasi + screenshot + 2 foto + info HP sekaligus), dan
kode yang tersimpan di browser supaya tidak perlu diketik ulang.

### Foto pencuri di layar kunci
Dari panel: **Foto Maling di Layar Kunci** → pilih file → **Kirim Foto**.
Foto langsung tampil di layar kunci HP, disertai teks ancaman + lokasi
live. Disimpan di internal storage HP sehingga **bertahan setelah reboot**.
Hapus lagi kapan saja dari panel. Teks ancamannya bisa diganti juga
(**Kirim teks ancaman**).

### Jebakan otomatis
Tanpa perlu kirim perintah, app bereaksi sendiri saat:
- **PIN salah 3×** → foto + sirene + SMS ke nomor kamu
- **SIM dicabut** → HP terkunci + foto + sirene + SMS

Counter reset otomatis begitu kamu berhasil buka kunci dengan PIN benar.

> Broker MQTT-nya publik, jadi **kode pairing adalah password** — jangan bagikan
> ke siapa pun. Verifikasi: `am/{kode}/cmd` → jangan ketik manual.

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

## 3b. WAJIB: Whitelist XOS 15 (Infinix / Tecno / itel)

**Kode aplikasinya sudah benar, tapi XOS punya battery killer sendiri yang
mematikan app di background.** Langkah 3 dan 7 yang paling sering terlewat —
pengaturan sistem sudah semua benar, tapi cleaner bawaan tetap menutup app
dengan jadwalnya sendiri.

| # | Menu | Yang diubah |
|---|------|------------|
| 1 | Settings > Apps > AntiMaling > **Autostart** | **ON** — supaya hidup lagi setelah reboot |
| 2 | Settings > Battery > **Battery optimisation** > AntiMaling | **Don't optimise** |
| 3 | Security > **Battery & performance > Activity control** > AntiMaling | **No restrictions** |
| 4 | Settings > Battery > Battery optimisation > **⋮ > Advanced optimization** | Matikan **App battery usage optimization** |
| 5 | Settings > Battery > Battery optimisation > **⋮ > Deep Optimization** | **OFF** |
| 6 | Cari "**App launch**" di Settings > matikan "Manage automatically" | Nyalakan **Auto-launch** + **Secondary launch** + **Run in background** |
| 7 | **Phone Manager** > PowerMaster > Settings | Matikan **"Clean in standby"** dan **"Block app auto-launch"** |
| 8 | Dari daftar aplikasi terbaru (Recents) | **Kunci** kartu AntiMaling (tarik ke bawah) |

Selesai? Cek di app: bagian **"6. Kesehatan Service"** — semua baris harus
`YA`, dan `Foreground` harus `OK`. Kalau ada yang `TIDAK` atau `GAGAL`, tabel
di atas belum tuntas.

> Kalau masih dibunuh juga setelah 8 langkah ini, penyebabnya sudah di luar
> jangkauan aplikasi. Satu-satunya cara menutupnya adalah Device Owner
> (`adb shell dpm set-device-owner`, butuh factory reset tanpa akun Google)
> atau root.

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
  AntiMalApp.kt
  util/Prefs.kt, util/Perms.kt, util/Consent.kt, util/CodeGen.kt, util/MalingPhoto.kt
  receiver/MyAdminReceiver.kt, BootReceiver.kt, SmsReceiver.kt, SimChangeReceiver.kt
  service/GuardService.kt, OverlayService.kt, CamService.kt,
          KeepAlive.kt, KeepAliveJob.kt, KeepAliveWorker.kt
  control/CommandHandler.kt, RingManager.kt, FlashManager.kt, LocateManager.kt, TheftGuard.kt
  spy/ShotTaker.kt, CamSnap.kt, DeviceInfo.kt
  lock/LockActivity.kt, ui/MainActivity.kt, ui/ShotConsentActivity.kt
laptop-panel/  (server.py, index.html, app.js, styles.css)
```
