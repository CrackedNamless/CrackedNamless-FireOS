#!/usr/bin/env bash
# Turns the Home takeover off, gives the Home button back to Amazon and brings the screensaver back. The app stays installed.
# Usage: tools/undo.sh <fire-tv-ip> [--uninstall]
set -euo pipefail
export MSYS_NO_PATHCONV=1

IP="${1:?usage: $0 <fire-tv-ip> [--uninstall]}"
PKG=com.crackednamless.fireos
SVC="$PKG/$PKG.HomeService"

command -v adb >/dev/null || { echo "adb not found: install Android Platform Tools first"; exit 1; }
adb connect "$IP:5555" >/dev/null
A="adb -s $IP:5555"

CUR="$($A shell settings get secure enabled_accessibility_services | tr -d '\r')"
[ "$CUR" = "null" ] && CUR=""
NEW="$(echo ":$CUR:" | sed "s#:$SVC:#:#g; s#^:##; s#:\$##")"
if [ -z "$NEW" ]; then
  $A shell settings delete secure enabled_accessibility_services
  $A shell settings put secure accessibility_enabled 0
else
  $A shell settings put secure enabled_accessibility_services "$NEW"
fi
$A shell pm enable com.amazon.ftv.screensaver >/dev/null 2>&1 || true
$A shell settings put secure screensaver_enabled 1
$A shell input keyevent KEYCODE_HOME

if [ "${2:-}" = "--uninstall" ]; then
  $A uninstall "$PKG"
fi
echo "Done."
