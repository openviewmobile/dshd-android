#!/usr/bin/env bash
# Export the already-provisioned app rootfs as the first-run .tar.gz payload.
# The resulting archive is intentionally NOT bundled in the APK; host it on a
# release endpoint (or push it to the device for testing).
set -euo pipefail
PKG="${PKG:-com.dshd.android}"
OUT="${1:-dist/dshd-rootfs-arm64.tar.gz}"
mkdir -p "$(dirname "$OUT")"

adb exec-out run-as "$PKG" tar -cf - -C files/rootfs . \
  | gzip -1 > "$OUT"

ls -lh "$OUT"
sha256sum "$OUT" 2>/dev/null || shasum -a 256 "$OUT"
