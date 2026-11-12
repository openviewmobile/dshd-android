#!/usr/bin/env bash
# Add the Chromium 92 polyfills to a host-side rootfs copy. The app asset is the
# source of truth so the APK and the served frontend stay in sync.
set -euo pipefail
ROOT="${1:?usage: patch-rootfs-webview.sh <rootfs-dir>}"
ASSET="$(cd "$(dirname "$0")/.." && pwd)/app/app/src/main/assets/dsh-index.html"
TARGET="$ROOT/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-web-frontend/dist/index.html"
[ -f "$TARGET" ] || { echo "missing $TARGET" >&2; exit 1; }
python3 - "$ASSET" "$TARGET" <<'PY'
import sys
asset, target = sys.argv[1:]
block = open(asset).read().split('<script>', 1)[1].split('</script>', 1)[0]
text = open(target).read()
marker = '<script>'
if marker in text:
    head, rest = text.split(marker, 1)
    _, tail = rest.split('</script>', 1)
    text = head + marker + '\n' + block + '\n</script>' + tail
else:
    text = text.replace('<title>DeepSeek Harness</title>',
                        '<title>DeepSeek Harness</title>\n    <script>' + block + '</script>', 1)
open(target, 'w').write(text)
print('patched', target)
PY
"$(cd "$(dirname "$0")" && pwd)/patch-rootfs-chromium92.sh" "$ROOT"
