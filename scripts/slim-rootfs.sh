#!/usr/bin/env bash
# Strip Node debug symbols and remove host-platform prebuilds from an unpacked
# rootfs. The result still runs on Android arm64; no user-facing feature is
# removed. Requires an aarch64-capable strip (Linux binutils, llvm-strip, or
# STRIP=/path/to/strip).
#
#   scripts/slim-rootfs.sh /tmp/rootfs [--clean]
#
# --clean additionally removes source maps, TypeScript declarations, tests and
# documentation. It is optional because it removes debugging/type assets.
set -euo pipefail
ROOT="${1:?usage: slim-rootfs.sh <rootfs-dir> [--clean]}"
CLEAN=0
[ "${2:-}" = "--clean" ] && CLEAN=1
NODE="$ROOT/opt/node24/bin/node"
NODE_MODULES="$ROOT/opt/node24/lib/node_modules"
DSH_MODULES="$NODE_MODULES/@deepseek-ai/dsh/node_modules"
NODE_PTY="$DSH_MODULES/node-pty"
PNPM="$NODE_MODULES/pnpm"

[ -f "$NODE" ] || { echo "missing $NODE" >&2; exit 2; }
[ -d "$NODE_PTY" ] || { echo "missing $NODE_PTY" >&2; exit 2; }

if file "$NODE" | grep -q 'not stripped'; then
  if [ -n "${STRIP:-}" ]; then
    "$STRIP" --strip-unneeded "$NODE"
  elif command -v aarch64-linux-gnu-strip >/dev/null 2>&1; then
    aarch64-linux-gnu-strip --strip-unneeded "$NODE"
  elif command -v llvm-strip >/dev/null 2>&1; then
    llvm-strip --strip-unneeded "$NODE"
  else
    echo "No aarch64 strip found. Set STRIP to a host-capable strip binary." >&2
    echo "On macOS use Docker: docker run --rm --platform linux/arm64 -v \"$(dirname "$NODE"):/out\" debian:bookworm-slim sh -c 'apt-get update && apt-get install -y binutils && strip --strip-unneeded /out/node'" >&2
    exit 3
  fi
fi

# node-pty ships Windows/macOS/x64 binaries; Android arm64 only needs this one.
for platform in win32-x64 win32-arm64 darwin-x64 darwin-arm64 linux-x64; do
  rm -rf "$NODE_PTY/prebuilds/$platform"
done
rm -rf "$NODE_PTY/third_party/conpty"

if [ -d "$PNPM/dist" ]; then
  find "$PNPM/dist" -maxdepth 1 -type f \( -name 'reflink.darwin*' -o -name 'reflink.win32*' \) -delete
  find "$PNPM/dist/vendor" -maxdepth 1 -type f -name 'fastlist-*-x64.exe' -delete 2>/dev/null || true
fi

if [ "$CLEAN" -eq 1 ]; then
  find "$NODE_MODULES" -type f \( -name '*.map' -o -name '*.d.ts' \) -delete
  find "$NODE_MODULES" -type d \( -name test -o -name tests -o -name __tests__ \) \
    -prune -exec rm -rf {} + 2>/dev/null || true
  rm -rf "$ROOT/usr/share/doc" "$ROOT/usr/share/man" "$ROOT/usr/share/info"
  rm -rf "$NODE_MODULES/npm/docs" "$NODE_MODULES/npm/man"
fi

echo "rootfs: $ROOT"
echo "node:   $(du -h "$NODE" | awk '{print $1}')"
echo "size:   $(du -sh "$ROOT" | awk '{print $1}')"
