# Full embedded-rootfs APK release

The normal `Android APK` workflow builds a **lite debug APK** for every push.
`Full APK release` builds the production APK with the runtime embedded and
attaches it to a GitHub Release. **No local APK is uploaded.**

## Prerequisites

1. The sanitized rootfs `.tar.xz` is published at
   <https://github.com/openviewmobile/dshd-android/releases/tag/rootfs-0.3.2>
   (SHA-256 `0c91ec900505963afe6cb7e2240bd0161a1d59bd46dc1a6e3a397c7395b18635`).
   It contains no `.ssh`, host SSH private keys, provider credentials, logs, or
   local user paths.
2. Repository secrets:
   - `ANDROID_KEYSTORE_BASE64` — base64 of the release `.jks`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. Optionally repository variables `ROOTFS_URL` and `ROOTFS_SHA256` for tag
   builds.

Never commit the keystore, its password, or a rootfs containing credentials.

## Run

From GitHub: **Actions → Full APK release → Run workflow**. The defaults point
to the published `rootfs-0.3.2` asset and tag `v0.3.2`; no parameters are
required.

The workflow:

1. downloads and SHA-256 verifies the rootfs;
2. decodes the keystore into the runner temp directory;
3. builds `assembleRelease` with `DSHD_EMBED_ROOTFS`, using
   `scripts/build-release-apk.sh`;
4. verifies the APK signature and writes a SHA-256 file;
5. uploads a workflow artifact;
6. attaches the APK and checksum to the GitHub Release.

The signing password is passed only through environment variables and is never
printed. The release keystore must have an offline backup before this is used
for a production upgrade path.
