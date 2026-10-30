#!/usr/bin/env bash
# Installs CrackedNamless-FireOS on a Fire TV over the network and switches it on.
# On the first install it also turns off Amazon's FireTV Troubleshooting app (undo: adb shell pm enable com.amazon.firetv.troubleshooting).
# Usage: tools/install.sh <fire-tv-ip> [path/to/CrackedNamless-FireOS.apk]
# Needs adb (Android Platform Tools) and "ADB debugging" enabled on the Fire TV.
# Without an APK path it downloads the latest release.
set -euo pipefail
export MSYS_NO_PATHCONV=1

IP="${1:?usage: $0 <fire-tv-ip> [apk]}"
APK="${2:-}"
PKG=com.crackednamless.fireos
SVC="$PKG/$PKG.HomeService"
URL=https://github.com/CrackedNamless/CrackedNamless-FireOS/releases/latest/download/CrackedNamless-FireOS.apk

command -v adb >/dev/null || { echo "adb not found: install Android Platform Tools first"; exit 1; }

if [ -z "$APK" ]; then
  APK="$(mktemp -d)/CrackedNamless-FireOS.apk"
  echo "Downloading the latest release..."
  curl -fL --progress-bar -o "$APK" "$URL"
fi

echo "Connecting to $IP (accept the debugging prompt on the TV if it appears)..."
adb connect "$IP:5555" >/dev/null
A="adb -s $IP:5555"
$A wait-for-device

FIRST=1
if $A shell pm list packages "$PKG" | grep -q "^package:$PKG"; then FIRST=0; fi

echo "Installing..."
$A install -r "$APK"

echo "Granting permissions..."
$A shell pm grant "$PKG" android.permission.READ_LOGS
$A shell cmd notification allow_listener "$PKG/$PKG.MediaListener" >/dev/null

echo "Turning on the Home service..."
CUR="$($A shell settings get secure enabled_accessibility_services | tr -d '\r')"
[ "$CUR" = "null" ] && CUR=""
case ":$CUR:" in
  *":$SVC:"*) ;;
  *) $A shell settings put secure enabled_accessibility_services "${CUR:+$CUR:}$SVC" ;;
esac
$A shell settings put secure accessibility_enabled 1

if [ "$FIRST" = 1 ]; then
  echo "First install: turning off Amazon's FireTV Troubleshooting app..."
  $A shell pm disable-user --user 0 com.amazon.firetv.troubleshooting >/dev/null 2>&1 || true
fi

$A shell am start -n "$PKG/.HomeActivity" >/dev/null
echo "Done. Press the Home button on the remote."
