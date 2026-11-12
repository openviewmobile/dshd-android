#!/usr/bin/env bash
# Inject the mobile WebView layout fix into a dsh-web frontend index.html.
set -euo pipefail
TARGET="${1:?usage: patch-rootfs-mobile-css.sh <index.html>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
CSS="$HERE/../rootfs/mobile.css"
JS="$HERE/../rootfs/mobile-fix.js"
[ -f "$TARGET" ] || { echo "missing $TARGET" >&2; exit 1; }
[ -f "$CSS" ] || { echo "missing $CSS" >&2; exit 1; }
[ -f "$JS" ] || { echo "missing $JS" >&2; exit 1; }
python3 - "$TARGET" "$CSS" "$JS" <<'PY'
import sys
target, css_path, js_path = sys.argv[1], sys.argv[2], sys.argv[3]
css = open(css_path).read().rstrip()
js = open(js_path).read().rstrip()
html = open(target).read()
start = '<style id="dshd-mobile-fix">'
end = '</style>'
if start in html:
    head, rest = html.split(start, 1)
    _, tail = rest.split(end, 1)
    html = head + start + '\n' + css + '\n' + end + tail
elif '</head>' in html:
    html = html.replace('</head>', start + '\n' + css + '\n' + end + '\n</head>', 1)
else:
    raise SystemExit('no </head> in ' + target)
script_id = '<script id="dshd-mobile-fix-js">'
script_end = '</script>'
if script_id in html:
    head, rest = html.split(script_id, 1)
    _, tail = rest.split(script_end, 1)
    html = head + script_id + '\n' + js + '\n' + script_end + tail
elif '</body>' in html:
    html = html.replace('</body>', script_id + '\n' + js + '\n' + script_end + '\n</body>', 1)
else:
    raise SystemExit('no </body> in ' + target)
open(target, 'w').write(html)
print('patched', target)
PY
