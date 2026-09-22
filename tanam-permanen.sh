#!/bin/bash
# Tanam permanen AntiMaling — jalankan dari PC dengan ADB
set -e
PKG="com.antimaling.permanen"
APK="${1:-app-debug.apk}"
echo "[1] Install $APK ..."
adb devices
adb install -r "$APK" || adb install "$APK"
echo "[2] Grant izin ..."
adb shell pm grant $PKG android.permission.READ_SMS || true
adb shell pm grant $PKG android.permission.RECEIVE_SMS || true
adb shell pm grant $PKG android.permission.SEND_SMS || true
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant $PKG android.permission.CAMERA || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true
echo "[3] Coba set device-owner (hanya bisa di HP fresh-reset tanpa akun) ..."
adb shell dpm set-device-owner $PKG/.receiver.MyAdminReceiver || echo "Lewati device-owner (wajar jika HP sudah ada akun). Device Admin manual tetap cukup."
echo "SELESAI. Buka AntiMaling 1x untuk isi PIN + nomor trusted."
