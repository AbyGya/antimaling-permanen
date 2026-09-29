#!/usr/bin/env bash
# Diagnosis AntiMaling — cek langsung ke HP lewat adb.
#
# Jalankan:
#   ./diagnose.sh
#
# Butuh: HP tersambung USB, USB Debugging ON, sudah pernah authorize.
# Tujuannya: tahu PERSIS kenapa app dibunuh, bukan menebak.

set -u
PKG="com.antimaling.permanen"

echo "=============================================="
echo "  AntiMaling — Diagnosis HP"
echo "=============================================="
echo

DEV=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
if [ -z "${DEV:-}" ]; then
  echo "❌ Tidak ada HP tersambung."
  echo
  echo "Perbaiki:"
  echo "  1. Colok HP dengan kabel USB"
  echo "  2. Developer Options > USB Debugging = ON"
  echo "  3. Accept prompt 'Allow USB debugging' di layar HP"
  echo "  4. Jalankan ulang: adb devices  (harus=device, bukan unauthorized)"
  exit 1
fi
echo "✅ HP tersambung: $DEV"
echo

hr() { echo "----------------------------------------------"; }

hr; echo "1. STATUS APP"
adb shell pm list packages | grep -q "$PKG" \
  && echo "✅ APK terpasang" || echo "❌ APK belum terpasang"

adb shell dumpsys package "$PKG" 2>/dev/null | grep -E "versionName|versionCode" | head -2 | sed 's/^/   /'

hr; echo "2. SERVICE GUARD — hidup atau mati?"
SVC=$(adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -c "GuardService")
if [ "$SVC" -gt 0 ]; then
  echo "✅ GuardService SEDANG JALAN"
  adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -A2 "GuardService" | head -12 | sed 's/^/   /'
else
  echo "❌ GuardService MATI"
fi
echo
for S in CamService OverlayService; do
  N=$(adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -c "$S")
  [ "$N" -gt 0 ] && echo "   ✅ $S jalan" || echo "   ➖ $S tidak jalan (normal)"
done

hr; echo "3. KENAPA MATI? (ApplicationExitInfo)"
adb shell dumpsys activity exit-info "$PKG" 2>/dev/null | grep -E "reason=|timestamp=|description=" | tail -24 | sed 's/^/   /' \
  || echo "   (tidak ada data — app belum pernah dibunuh)"

hr; echo "4. WHITELIST BATTERY — sudah di-whitelist?"
adb shell dumpsys deviceidle whitelist 2>/dev/null | grep -q "$PKG" \
  && echo "✅ Ada di deviceidle whitelist" || echo "❌ TIDAK ada di deviceidle whitelist  <-- INI MASALAH"
echo
adb shell cmd appops get "$PKG" RUN_ANY_IN_BACKGROUND 2>/dev/null | sed 's/^/   op RUN_ANY_IN_BACKGROUND: /'
adb shell cmd appops get "$PKG" RUN_IN_BACKGROUND 2>/dev/null | sed 's/^/   op RUN_IN_BACKGROUND  : /'
adb shell appops get "$PKG" 2>/dev/null | grep -iE "BACKGROUND|ALARM|START_FOREGROUND" | head -8 | sed 's/^/   /'

hr; echo "5. AUTOSTART / IZIN KHUSUS"
adb shell cmd appops get "$PKG" REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 2>/dev/null | sed 's/^/   /'
adb shell dumpsys deviceidle get deep 2>/dev/null | sed 's/^/   deep idle: /'
adb shell dumpsys deviceidle get light 2>/dev/null | sed 's/^/   light idle: /'

hr; echo "6. NOTIFIKASI (kalau dimatikan, XOS bisa bunuh FGS)"
NID=$(adb shell dumpsys notification --noredact 2>/dev/null | grep -A3 "$PKG" | grep -oE "NotificationRecord\(.*" | head -1)
[ -n "${NID:-}" ] && echo "   $NID" || echo "   (tidak ada notifikasi aktif)"

hr; echo "7. LOGCAT — error service (15 detik terakhir)"
adb logcat -d -t 400 2>/dev/null \
  | grep -iE "antimaling|GuardService|CamService|AndroidRuntime" \
  | grep -viE "^$" | tail -25 | sed 's/^/   /'
if [ $? -ne 0 ]; then echo "   (tidak ada)"; fi

hr; echo "8. LOGCAT LANGSUNG — tes 20 detik REAL TIME"
echo "   Sekarang: tekan KUNCI dari panel, lalu tutup app HP."
echo "   Tunggu hitungannya selesai..."
for i in $(seq 20 -1 1); do
  L=$(adb logcat -d -t 60 2>/dev/null | grep -iE "GuardService|ForegroundService|antimaling" | tail -1)
  [ -n "${L:-}" ] && echo "   [$i] $L"
  sleep 1
done

hr
echo "SELESAI."
echo
echo "Cara baca:"
echo "  ForegroundServiceDidNotStartInTime / SecurityException"
echo "    -> Android menolak startForeground. Kirim output lengkap ke saya."
echo "  Killing <pkg> / ANR in $PKG"
echo "    -> XOS yang membunuhnya. Perlu whitelist 8 langkah."
echo "  Tidak ada error sama sekali tapi service tetap mati"
echo "    -> XOS membunuhnya diam-diam. Sama: butuh whitelist."
echo
echo "Kirim semua output di atas ke saya — dari situ baru bisa dipastikan"
echo "akarnya, tanpa perlu menebak lagi."
