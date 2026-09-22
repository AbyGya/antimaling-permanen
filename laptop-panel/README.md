# 💻 Panel Kontrol Laptop

Kontrol HP kapan saja selama HP online. Tanpa IP publik / port-forward — lewat Firebase.

## Setup sekali (5 menit)

1. **Firebase**: console.firebase.google.com → buat project → aktifkan:
   - **Firestore Database** (mode production) → tab **Rules** → copy isi `../firestore.rules` → Publish
   - **Authentication** → Sign-in method → aktifkan **Anonymous**
   - Project settings → salin **Web API Key** + **Project ID**
2. **HP**: buka AntiMaling → bagian *5. Kontrol Laptop* → tempel API Key + Project ID → *Simpan & Generate* → catat **kode pairing 6 digit**. Klik juga *Aktifkan Sadap Layar* (wajib ulang tiap reboot HP).
3. **Laptop**: edit `firebase-config.js` (apiKey + projectId) → jalankan:
   ```bash
   cd laptop-panel
   python3 server.py
   ```
   Browser terbuka otomatis → masukkan kode pairing → **Sambungkan**.

## Pakai
- Status **online** = HP kirim heartbeat < 30 dtk. Polling HP tiap 10 dtk.
- Tombol: Ping, Info, Kunci, Dering MAX, Stop, Lacak, **Screenshot**, **Foto depan/belakang**, Senter, Overlay ON/OFF, teks custom, buka kunci (PIN).
- Hasil (teks + foto) muncul realtime di feed. Buka kunci butuh PIN pemilik.

## Batasan jujur
- Screenshot butuh izin screen-capture yang **hangus tiap reboot** (aturan Android, semua app sama).
- Foto background butuh izin kamera + HP tidak dalam mode Doze berat.
- Jangan dipakai di HP orang lain tanpa izin — melanggar hukum (UU ITE).
