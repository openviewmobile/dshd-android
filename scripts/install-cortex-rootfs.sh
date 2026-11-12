#!/usr/bin/env bash
# Prototype provisioning: copy the Cortex Ubuntu rootfs (from the Apache-2.0
# Cortex APK) into dshd's private storage and apply the Android/WebView fixes.
# The final product must bootstrap the rootfs itself; this exists for repeatable
# device testing on a rooted phone.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$HERE/app/app/src/main/assets"
PKG=com.dshd.android
SRC=/data/data/org.cortex.terminal/files/cortex
DST=/data/data/$PKG/files/rootfs
DSH="$DST/opt/node24/lib/node_modules/@deepseek-ai/dsh"
WEB="$DSH/node_modules/@deepseek-ai/dsh-web-frontend/dist"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

adb shell "test -d $SRC" || { echo "Cortex rootfs missing; install Cortex and open it once." >&2; exit 1; }
adb shell "rm -rf $DST; cp -a $SRC $DST"

# Default Workspace needs a Linux Documents directory; Cortex has no xdg-user-dir.
cat > "$TMP/xdg-user-dir" <<'SHIM'
#!/bin/sh
echo "$HOME/Documents"
SHIM
adb push "$TMP/xdg-user-dir" "$DST/usr/local/bin/xdg-user-dir" >/dev/null
adb shell "chmod 755 $DST/usr/local/bin/xdg-user-dir; mkdir -p $DST/home/Documents"

# Chromium 92 WebView cannot run the stock frontend.
if [ -f "$ASSETS/dsh-frontend-chrome92.zip" ]; then
  unzip -q "$ASSETS/dsh-frontend-chrome92.zip" -d "$TMP/assets"
  adb push "$TMP/assets/." "$WEB/assets/" >/dev/null
  adb push "$ASSETS/dsh-index.html" "$WEB/index.html" >/dev/null
fi

# The copied profile has no root Include on settings reload; hide the preview
# notice until the Host-side settings/profile bug is fixed.
CLIENT="$DSH/node_modules/@deepseek-ai/dsh-client-ui-settings-models/lib/client.js"
adb shell "sed -i 's/function WelcomeNotice(props) {/function WelcomeNotice(props) { return null;/' $CLIENT"

uid="$(adb shell stat -c %u "/data/data/$PKG/files")"
adb shell "chown -R $uid:$uid $DST; restorecon -R $DST"
adb shell "du -sh $DST"
