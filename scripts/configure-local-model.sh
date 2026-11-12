#!/usr/bin/env bash
# Push the local dsh provider profile + credentials into the connected Android
# app for a real-model smoke test. Never commit the generated credentials.
#
#   DSH_GATEWAY_BASE_URL=http://192.0.2.10:3000/v1 \
#     projects/android/scripts/configure-local-model.sh
#
# The device cannot resolve hostnames like "ds923plus", so pass the gateway's
# LAN address explicitly. The script derives the provider/model list from
# ~/.dsh/profiles/web/cordis.patch.yml and the API key from
# ~/.dsh/.credentials.yaml.
set -euo pipefail
cd "$(dirname "$0")/.."
PACKAGE=com.dshd.android
DSH_HOME=${DSH_HOME:-$HOME/.dsh}
ADB=${ADB:-adb}
BASE_URL=${DSH_GATEWAY_BASE_URL:-}
PATCH="$DSH_HOME/profiles/web/cordis.patch.yml"
CREDS="$DSH_HOME/.credentials.yaml"

[[ -f "$PATCH" ]] || { echo "missing $PATCH" >&2; exit 1; }
[[ -f "$CREDS" ]] || { echo "missing $CREDS" >&2; exit 1; }
[[ -n "$BASE_URL" ]] || { echo "set DSH_GATEWAY_BASE_URL (e.g. http://192.0.2.10:3000/v1)" >&2; exit 2; }
"$ADB" get-state >/dev/null

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
python3 - "$PATCH" "$CREDS" "$BASE_URL" "$TMP" <<'PY'
import re, sys
from pathlib import Path
patch, creds, base_url, tmp = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], Path(sys.argv[4])
text = patch.read_text()
if 'baseURL:' not in text:
    raise SystemExit('provider patch has no baseURL')
text = re.sub(r'(?m)^(\s*baseURL:\s*).*$', lambda m: m.group(1) + base_url, text)
(tmp / 'cordis.patch.yml').write_text(text)
(tmp / '.credentials.yaml').write_text(creds.read_text())
PY
chmod 600 "$TMP/.credentials.yaml"

"$ADB" push "$TMP/cordis.patch.yml" /data/local/tmp/dshd-cordis.patch.yml >/dev/null
"$ADB" push "$TMP/.credentials.yaml" /data/local/tmp/dshd-credentials.yaml >/dev/null
"$ADB" shell "run-as $PACKAGE cp /data/local/tmp/dshd-cordis.patch.yml files/rootfs/home/.dsh/profiles/web/cordis.patch.yml"
"$ADB" shell "run-as $PACKAGE cp /data/local/tmp/dshd-credentials.yaml files/rootfs/home/.dsh/.credentials.yaml"
"$ADB" shell "run-as $PACKAGE chmod 600 files/rootfs/home/.dsh/profiles/web/cordis.patch.yml files/rootfs/home/.dsh/.credentials.yaml"
"$ADB" shell "am force-stop $PACKAGE"
"$ADB" shell "am start -n $PACKAGE/.MainActivity" >/dev/null
echo "pushed provider profile (${BASE_URL}); app restarted"
