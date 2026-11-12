#!/usr/bin/env bash
# Start the app, wait for the WebView, then run android-e2e.js.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG=com.dshd.android
PORT="${CDP_PORT:-9224}"
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
for _ in $(seq 1 60); do
  PID="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)"
  [ -n "$PID" ] || { sleep 1; continue; }
  adb forward "tcp:$PORT" "localabstract:webview_devtools_remote_$PID" >/dev/null 2>&1 || true
  if curl -fsS "http://127.0.0.1:$PORT/json" 2>/dev/null | grep -q '"type": "page"'; then
    CDP_PORT="$PORT" node "$ROOT/scripts/android-e2e.js"
    exit $?
  fi
  sleep 1
done
echo "WebView DevTools endpoint did not become ready" >&2
exit 1
