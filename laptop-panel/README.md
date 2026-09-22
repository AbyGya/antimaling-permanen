# 💻 Panel Kontrol Laptop (MQTT — tanpa setup)

Kontrol HP kapan saja selama HP online. Tanpa daftar akun, tanpa API key,
tanpa Firebase Console. HP dan laptop bertemu lewat kode 6 digit di broker publik.

## Pakai (2 menit)

1. **HP**: install APK → buka app 1x → kode 6 digit tampil besar otomatis.
2. **Laptop**:
   ```bash
   cd laptop-panel
   python3 server.py
   ```
   Browser terbuka → ketik 6 digit → **Sambungkan** → status **● online**.
3. Klik perintah: Ping, Info, Kunci, Dering MAX, Stop, Lacak, Screenshot,
   Foto depan/belakang, Senter, Overlay ON/OFF, teks custom, buka kunci (PIN).
   Hasil (teks + foto) muncul realtime di feed.

## Cara kerja
- Topik `am/{kode}/cmd` (laptop → HP), `am/{kode}/res` (HP → laptop),
  `am/{kode}/status` (online/offline akurat via retained + LWT).
- Broker: `broker.emqx.io` (publik, gratis). Kode acak = privasi (topik tak bisa ditebak).
- Ganti kode kapan saja via tombol **Ganti Kode Baru** di HP.

## Batasan jujur
- Butuh internet di kedua sisi. Broker publik best-effort (untuk pribadi lebih dari cukup).
- Screenshot butuh izin screen-capture yang hangus tiap reboot (aturan Android).
- Hanya untuk HP milik sendiri — sadap HP orang tanpa izin melanggar UU ITE.
