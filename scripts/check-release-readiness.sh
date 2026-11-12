#!/usr/bin/env bash
# Gate a release APK before publishing. Exits non-zero while production inputs are missing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/dist/dshd-android-release.apk}"
KEYSTORE="${DSHD_KEYSTORE:-}"
URL="${DSHD_ROOTFS_URL:-}"
SHA="${DSHD_ROOTFS_SHA256:-}"
EMBEDDED=0
fail=0

check() { if eval "$1"; then echo "PASS: $2"; else echo "FAIL: $2"; fail=1; fi; }

check "[ -f \"$APK\" ]" "APK exists: $APK"
if [ -f "$APK" ]; then
  APKSIGNER="${APKSIGNER:-$(command -v apksigner || true)}"
  [ -n "$APKSIGNER" ] || APKSIGNER="$(find "$HOME/Library/Android/sdk/build-tools" -name apksigner 2>/dev/null | sort | tail -1)"
  if [ -n "$APKSIGNER" ]; then
    check "\"$APKSIGNER\" verify --print-certs \"$APK\" >/dev/null 2>&1" "APK v2/v3 signature verifies"
  else
    echo "SKIP: apksigner not found"; fail=1
  fi
  check "! unzip -p \"$APK\" classes.dex | strings | grep -Eq '127\.0\.0\.1[^ ]*rootfs|rootfs[^ ]*127\.0\.0\.1'" "no loopback rootfs URL in classes.dex"
  if unzip -l "$APK" | grep -q 'assets/rootfs.tar.xz'; then
    EMBEDDED=1
    echo "PASS: APK embeds assets/rootfs.tar.xz"
    set +o pipefail
    MAGIC="$(unzip -p "$APK" assets/rootfs.tar.xz | head -c 6 | od -An -tx1 | tr -d ' \n')"
    set -o pipefail
    check "[ \"$MAGIC\" = fd377a585a00 ]" "embedded rootfs is xz"
  fi
fi
check "[ -n \"$KEYSTORE\" ] && [ -f \"$KEYSTORE\" ]" "DSHD_KEYSTORE points to a file"
case "$KEYSTORE" in
  ""|/tmp/dshd-test.jks|*test*) echo "FAIL: keystore missing or looks like a test key ($KEYSTORE)"; fail=1 ;;
  *) echo "PASS: keystore is not the known test key" ;;
esac
if [ "$EMBEDDED" -eq 1 ]; then
  echo "PASS: rootfs is embedded; external DSHD_ROOTFS_URL not required"
else
  check "[ -n \"$URL\" ]" "DSHD_ROOTFS_URL is set (or embed rootfs)"
  case "$URL" in
    https://*) echo "PASS: rootfs URL uses https" ;;
    "") : ;;
    *) echo "FAIL: rootfs URL is not https ($URL)"; fail=1 ;;
  esac
  check "printf '%s' \"$SHA\" | grep -Eq '^[0-9a-fA-F]{64}$'" "ROOTFS SHA-256 is 64 hex chars"
fi
[ "$fail" -eq 0 ] || { echo; echo "Release is NOT ready."; exit 1; }
echo; echo "Release readiness checks passed."
