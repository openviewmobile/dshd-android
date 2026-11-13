# Full embedded-rootfs APK release

The normal `Android APK` workflow builds a **lite debug APK** for every push.
`Full APK release` builds the production APK with the runtime embedded and
attaches it to a GitHub Release. **No local APK is uploaded.**

## Prerequisites

1. A **sanitized** rootfs `.tar.xz` hosted at a public HTTPS URL (GitHub Release
   asset or object storage). It must not contain `.ssh`, host SSH private keys,
   provider credentials, logs, or local user paths.
2. Repository secrets:
   - `ANDROID_KEYSTORE_BASE64` — base64 of the release `.jks`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
3. Optionally repository variables `ROOTFS_URL` and `ROOTFS_SHA256` for tag
   builds.

Never commit the keystore, its password, or a rootfs containing credentials.

## Run

From GitHub: **Actions → Full APK release → Run workflow**, fill:

- `rootfs_url`
- `rootfs_sha256`
- `tag` (existing tag, e.g. `v0.3.2`)

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
