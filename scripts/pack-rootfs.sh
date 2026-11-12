#!/usr/bin/env bash
# Pack a host-side rootfs directory without macOS AppleDouble/xattrs.
# Android's tar extracts `com.apple.provenance` sidecars as `._name` files,
# which dsh's plugin metadata reader then rejects.
#
# Runtime state is excluded: shipping a seeded session/store/credential makes a
# fresh install restore an old session (with its pinned permission) and leaks
# per-device secrets. The default workspace is intentionally empty on first run.
set -euo pipefail
SRC="${1:?usage: pack-rootfs.sh <rootfs-dir> [out.tar.xz|out.tar.gz]}"
OUT="${2:-dist/dshd-rootfs-arm64.tar.gz}"
mkdir -p "$(dirname "$OUT")"
# Package-manager caches are re-creatable and must not ship in a rootfs.
case "$OUT" in
  *.tar.xz) COMPRESS=(xz -6 -T0) ;;
  *.tar.gz|*.tgz) COMPRESS=(gzip -9) ;;
  *) echo "output must end in .tar.xz or .tar.gz: $OUT" >&2; exit 2 ;;
esac
COPYFILE_DISABLE=1 tar --no-xattrs \
  --exclude='./home/.dsh/sessions' \
  --exclude='./home/.dsh/storages' \
  --exclude='./home/.dsh/logs' \
  --exclude='./home/.dsh/cache' \
  --exclude='./home/.dsh/.credentials.yaml' \
  --exclude='./home/.dsh/.anonymous-user-id' \
  --exclude='./home/Documents/deepseek-harness/default-workspace/*' \
  --exclude='./home/.npm/_cacache' \
  --exclude='./home/.npm/_logs' \
  --exclude='./var/cache/*' \
  --exclude='./var/lib/apt/lists/*' \
  --exclude='./var/log/*' \
  --exclude='./tmp/*' \
  -cf - -C "$SRC" . | "${COMPRESS[@]}" > "$OUT"
ls -lh "$OUT"
shasum -a 256 "$OUT"
