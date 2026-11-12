#!/usr/bin/env bash
# Android runs dsh outside a chroot. ripgrep resolves /home/... against the
# real Android root, so map logical absolute arguments into CORTEX_ROOT before
# spawning rg; map physical result paths back to logical for display.
set -euo pipefail
TARGET="${1:?usage: patch-rootfs-fs-search-paths.sh <dsh-tool-fs-search/lib/index.js>}"
[ -f "$TARGET" ] || { echo "missing $TARGET" >&2; exit 1; }
python3 - "$TARGET" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
changed = False

old_spawn = '''\t\thandle = ctx.subprocess.spawn({
\t\t\targv: [
\t\t\t\tawait resolveRgPath(),
\t\t\t\t"--no-config",
\t\t\t\t...argv
\t\t\t],'''
new_spawn = '''\t\tconst rootfs = process.env.CORTEX_ROOT;
\t\tconst searchArgv = rootfs === void 0 ? argv : argv.map((arg) => isAbsolute(arg) && (arg === "/home" || arg.startsWith("/home/") || arg === "/tmp" || arg.startsWith("/tmp/") || !existsSync(arg)) ? join(rootfs, arg) : arg);
\t\thandle = ctx.subprocess.spawn({
\t\t\targv: [
\t\t\t\tawait resolveRgPath(),
\t\t\t\t"--no-config",
\t\t\t\t...searchArgv
\t\t\t],'''
if old_spawn in s:
    s = s.replace(old_spawn, new_spawn, 1)
    changed = True
elif "const searchArgv = rootfs" not in s:
    raise SystemExit("spawn snippet not found in " + p)

old_display = '''function toWorkdirRelative(path, workdir) {
\tif (!isAbsolute(path)) return path;'''
new_display = '''function fromRootfsPath(path) {
\tconst rootfs = process.env.CORTEX_ROOT;
\tif (rootfs === void 0 || !isAbsolute(path)) return path;
\tconst prefix = rootfs.endsWith(sep) ? rootfs : `${rootfs}${sep}`;
\tif (path === rootfs) return "/";
\treturn path.startsWith(prefix) ? `/${path.slice(prefix.length)}` : path;
}
function toWorkdirRelative(path, workdir) {
\tpath = fromRootfsPath(path);
\tif (!isAbsolute(path)) return path;'''
if old_display in s:
    s = s.replace(old_display, new_display, 1)
    changed = True
elif "function fromRootfsPath(path)" not in s:
    raise SystemExit("display snippet not found in " + p)

if changed:
    open(p, "w").write(s)
    print("patched", p)
else:
    print("already patched", p)
PY
