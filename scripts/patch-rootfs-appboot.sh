#!/usr/bin/env bash
# Android web-profile fix: mountRootInclude looked up the loader with
# ctx.get("loader"), which can be undefined on Android even though ctx.loader is
# live, so it returned before recording the root Include and every HMR reload
# failed with "profile reload requires the root Include entry".
set -euo pipefail
ROOT="${1:?usage: patch-rootfs-appboot.sh <rootfs-dir>}"
F="$ROOT/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js"
[ -f "$F" ] || { echo "missing $F" >&2; exit 1; }
python3 - "$F" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
if 'ctx.get("loader") ?? ctx.loader' in s:
    print('already patched')
    sys.exit(0)
old = '''\tconst includeId = await ctx.loader.create(rootInclude);
\tconst loader = ctx.get("loader");
\tif (loader === void 0) return void 0;
\tconst entry = loader.resolve(includeId);'''
new = '''\tconst includeId = await ctx.loader.create(rootInclude);
\tconst loader = ctx.get("loader") ?? ctx.loader;
\tconst entry = loader.resolve(includeId);'''
assert old in s, 'mountRootInclude pattern not found'
s = s.replace(old, new, 1)
old2 = '''\t\tconst entry = bootstrapIncludes.get(ctx);
\t\tif (entry === void 0) throw new Error(`${binName}: profile reload requires the root Include entry`);'''
new2 = '''\t\tconst entry = bootstrapIncludes.get(ctx)
\t\t\t?? [...ctx.loader.entries()].find((row) => row.options.id === "include");
\t\tif (entry === void 0) throw new Error(`${binName}: profile reload requires the root Include entry`);'''
assert old2 in s, 'recompose pattern not found'
s = s.replace(old2, new2, 1)
open(p, 'w').write(s)
print('patched', p)
PY
