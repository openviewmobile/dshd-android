#!/usr/bin/env bash
# Install the debug APK and provision the Cortex-style rootfs on a rooted device.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
APK="$HERE/app/app/build/outputs/apk/debug/app-debug.apk"
PKG=com.dshd.android

adb install -r "$APK"
adb shell am start -n "$PKG/.MainActivity" >/dev/null || true
sleep 2
"$HERE/scripts/install-cortex-rootfs.sh"
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity"
