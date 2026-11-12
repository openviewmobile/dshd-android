#!/usr/bin/env bash
# Build a release APK. A production key must be supplied by the release owner:
#   DSHD_KEYSTORE=/secure/dshd-release.jks \
#   DSHD_KEYSTORE_PASSWORD=... DSHD_KEY_ALIAS=... DSHD_KEY_PASSWORD=... \
#   scripts/build-release-apk.sh
set -euo pipefail
if [ -n "${DSHD_ROOTFS_URL:-}" ] || [ -n "${DSHD_ROOTFS_SHA256:-}" ]; then
  : "${DSHD_ROOTFS_URL:?set both DSHD_ROOTFS_URL and DSHD_ROOTFS_SHA256}"
  : "${DSHD_ROOTFS_SHA256:?set both DSHD_ROOTFS_URL and DSHD_ROOTFS_SHA256}"
  printf '%s' "$DSHD_ROOTFS_SHA256" | grep -Eq '^[0-9a-fA-F]{64}$' || {
    echo "DSHD_ROOTFS_SHA256 must be 64 hex chars" >&2; exit 2; }
  case "$DSHD_ROOTFS_URL" in
    http://*|https://*) ;;
    *) echo "DSHD_ROOTFS_URL must be http(s)" >&2; exit 2 ;;
  esac
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/app"
if [ -z "${GRADLE:-}" ] && [ -x "$ROOT/app/gradlew" ]; then
  GRADLE="$ROOT/app/gradlew"
fi
GRADLE="${GRADLE:-$(find "$HOME/.gradle/wrapper/dists" -type f -path '*/bin/gradle' 2>/dev/null | head -1)}"
if [ -z "$GRADLE" ]; then
  echo "Gradle not found; set GRADLE=/path/to/gradle" >&2
  exit 1
fi
if [ -z "${DSHD_KEYSTORE:-}" ]; then
  echo "DSHD_KEYSTORE is required; refusing to produce an unsigned release" >&2
  exit 2
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" "$GRADLE" --no-daemon :app:assembleRelease
APK="$ROOT/app/app/build/outputs/apk/release/app-release.apk"
cp "$APK" "$ROOT/dist/dshd-android-release.apk"
echo "$ROOT/dist/dshd-android-release.apk"
