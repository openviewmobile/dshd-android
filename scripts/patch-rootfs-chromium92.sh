#!/usr/bin/env bash
# Copy the pinned Chromium 92 client-bundle compatibility patches into a rootfs.
set -euo pipefail
ROOT="${1:?usage: patch-rootfs-chromium92.sh <rootfs-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../support/chromium92"
BASE="$ROOT/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai"
[ -d "$BASE" ] || { echo "missing dsh node_modules in $ROOT" >&2; exit 1; }
install -m 600 "$SRC/client.terminal.js" "$BASE/dsh-client-ui-sidebar-terminal/lib/client.terminal.js"
install -m 600 "$SRC/client.pdf.js" "$BASE/dsh-client-ui-sidebar-documentpreview/lib/client.pdf.js"
install -m 600 "$SRC/client.resources.js" "$BASE/dsh-client-resources/lib/client.js"
echo "patched Chromium 92 client bundles in $ROOT"
