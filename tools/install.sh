#!/usr/bin/env bash
# Installs CrackedNamless-FireOS on a Fire TV over the network and switches it on.
# It also installs Aptoide TV, the app store behind the "Store" tab.
# Usage: tools/install.sh <fire-tv-ip> [path/to/CrackedNamless-FireOS.apk] [--no-store]
# Needs adb (Android Platform Tools) and "ADB debugging" enabled on the Fire TV.
# Without an APK path it downloads the latest release.
set -euo pipefail
export MSYS_NO_PATHCONV=1

STORE=1
ARGS=()
for a in "$@"; do
  case "$a" in
    --no-store) STORE=0 ;;
    *) ARGS+=("$a") ;;
  esac
done
IP="${ARGS[0]:?usage: $0 <fire-tv-ip> [apk] [--no-store]}"
APK="${ARGS[1]:-}"
PKG=com.crackednamless.fireos
SVC="$PKG/$PKG.HomeService"
URL=https://github.com/CrackedNamless/CrackedNamless-FireOS/releases/latest/download/CrackedNamless-FireOS.apk
STORE_PKG=cm.aptoidetv.pt
STORE_API="https://ws75.aptoide.com/api/7/app/get/package_name=$STORE_PKG"

command -v adb >/dev/null || { echo "adb not found: install Android Platform Tools first"; exit 1; }

md5_of() {
  if command -v md5sum >/dev/null; then md5sum "$1" | cut -d' ' -f1; else md5 -q "$1"; fi
}

# Downloads the current Aptoide TV build and checks it against the hash Aptoide publishes for it.
fetch_store() {
  local dir meta path md5
  dir="$(mktemp -d)"
  meta="$dir/meta.json"
  curl -fsS --max-time 30 -o "$meta" "$STORE_API" || return 1
  path="$(grep -o '"path":"https://pool.apk.aptoide.com/[^"]*\.apk"' "$meta" | head -1 | cut -d'"' -f4)"
  md5="$(grep -o '"md5sum":"[0-9a-f]\{32\}"' "$meta" | head -1 | cut -d'"' -f4)"
  [ -n "$path" ] && [ -n "$md5" ] || return 1
  case "$path" in *"$md5"*) ;; *) return 1 ;; esac
  curl -fL --progress-bar --max-time 300 -o "$dir/store.apk" "$path" || return 1
  [ "$(md5_of "$dir/store.apk")" = "$md5" ] || { echo "Store download failed its checksum, not installing it."; return 1; }
  STORE_APK="$dir/store.apk"
}

if [ -z "$APK" ]; then
  APK="$(mktemp -d)/CrackedNamless-FireOS.apk"
  echo "Downloading the latest release..."
  curl -fL --progress-bar -o "$APK" "$URL"
fi

echo "Connecting to $IP (accept the debugging prompt on the TV if it appears)..."
adb connect "$IP:5555" >/dev/null
A="adb -s $IP:5555"
$A wait-for-device

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

if [ "$STORE" = 1 ]; then
  if $A shell pm list packages "$STORE_PKG" | tr -d '\r' | grep -qx "package:$STORE_PKG"; then
    echo "App store (Aptoide TV) already installed."
  else
    echo "Installing the app store (Aptoide TV)..."
    if fetch_store; then
      $A install -r "$STORE_APK" || echo "Could not install the store. The Store tab will explain how to retry."
    else
      echo "Could not download the store. Run this script again later, or pass --no-store to skip it."
    fi
  fi
fi

$A shell am start -n "$PKG/.HomeActivity" >/dev/null
echo "Done. Press the Home button on the remote."
