#!/usr/bin/env bash
# Build the debug APK. Requires an Android SDK and a Gradle 8+/9 distribution.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
if [ -z "${GRADLE:-}" ] && [ -x "$HERE/gradlew" ]; then
  GRADLE="$HERE/gradlew"
fi
GRADLE="${GRADLE:-$(find "$HOME/.gradle/wrapper/dists" -type f -path '*/bin/gradle' 2>/dev/null | head -1)}"
if [ -z "$GRADLE" ]; then
  echo "Gradle not found; set GRADLE=/path/to/gradle" >&2
  exit 1
fi
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" "$GRADLE" --no-daemon :app:assembleDebug
ls -lh app/build/outputs/apk/debug/app-debug.apk
