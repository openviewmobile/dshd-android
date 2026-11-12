#!/usr/bin/env bash
# Copy the model-facing Android bridge plugin into a dsh rootfs tree before
# packing. The native shell writes the matching --patch insert at Host start.
set -euo pipefail
ROOTFS="${1:?usage: install-bridge-plugin.sh <rootfs-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../rootfs/android-bridge-plugin"
DEST="$ROOTFS/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-android-bridge"
[ -f "$SRC/lib/index.js" ] || { echo "missing $SRC/lib/index.js" >&2; exit 1; }
mkdir -p "$DEST"
cp -a "$SRC/." "$DEST/"
echo "installed Android bridge plugin into $DEST"
