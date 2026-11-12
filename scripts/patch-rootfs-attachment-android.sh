#!/usr/bin/env bash
# Make attachment storage durability Android-safe.
#
# upstream: attachment-local fsyncs every ancestor between DSH_HOME and `/`.
# Android's app sandbox makes /data/user/0 unreadable, so the first file upload
# fails with EACCES. Stop the ancestor walk at the first unreadable directory;
# every app-private directory below DSH_HOME is still fsynced.
set -euo pipefail
ROOTFS="${1:?usage: patch-rootfs-attachment-android.sh <rootfs-dir>}"
FILE="$ROOTFS/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-attachment-local/lib/index.js"
python3 - "$FILE" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
text = path.read_text()
old = """\tlet level = target;
\twhile (level !== stop) {
\t\tconst parent = dirname(level);
\t\tawait syncDirectory(parent);
\t\t/* v8 ignore next -- filesystem-root guard: callers pass a boundary that is an ancestor of path, so the walk reaches it first. */
\t\tif (parent === level) return;
\t\tlevel = parent;
\t}
}"""
new = """\tlet level = target;
\twhile (level !== stop) {
\t\tconst parent = dirname(level);
\t\ttry {
\t\t\tawait syncDirectory(parent);
\t\t} catch (error) {
\t\t\t/* Android sandbox: ancestors above the app-private root are not readable. */
\t\t\tif (error !== null && typeof error === "object" && (error.code === "EACCES" || error.code === "EPERM")) return;
\t\t\tthrow error;
\t\t}
\t\t/* v8 ignore next -- filesystem-root guard: callers pass a boundary that is an ancestor of path, so the walk reaches it first. */
\t\tif (parent === level) return;
\t\tlevel = parent;
\t}
}"""
if new in text:
    print("attachment-local: already patched")
elif old in text:
    path.write_text(text.replace(old, new, 1))
    print("attachment-local: patched")
else:
    raise SystemExit("attachment-local: expected source block not found")
PY
