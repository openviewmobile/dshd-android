# Build and release provenance

This page records how the public 0.3.2 APK was produced. All artifacts were
built or re-signed by GitHub Actions; no local APK was uploaded.

## Production APK

| Item | Value |
| --- | --- |
| Release | <https://github.com/openviewmobile/dshd-android/releases/tag/v0.3.2> |
| Asset | `dshd-android-release.apk` |
| Size | 150,656,182 bytes |
| SHA-256 | `aa4e9783e7af547227f9c22970645cfe6daca41b54f11aed814a18b3c7ba3079` |
| Signing certificate | DSHD Project Release, SHA-256 `1C:98:8D:F8:9D:62:51:A1:B6:C8:AA:1D:74:C3:5F:4C:39:4A:0E:C0:CF:52:A9:4A:A9:B6:AD:F3:EF:4D:36:EC` |
| Embedded rootfs SHA-256 | `0c91ec900505963afe6cb7e2240bd0161a1d59bd46dc1a6e3a397c7395b18635` |

## Source and build chain

1. **Full APK payload** was built by the `Full debug APK` workflow at
   <https://github.com/openviewmobile/dshd-android/actions/runs/43464194850>
   from commit `c5bff412b857c4392f45d147d241b9eaeec79fb5`. The workflow
   downloaded the sanitized rootfs (SHA-256 `0c91...`) and ran
   `assembleDebug` with `DSHD_EMBED_ROOTFS`.
2. **Production re-signing** was performed by
   <https://github.com/openviewmobile/dshd-android/actions/runs/43541016234>.
   It downloaded the full APK artifact, removed the debug v1 signature files,
   and re-signed the unchanged payload with the release keystore from GitHub
   Actions Secrets.
3. The **sanitized rootfs** was extracted from the signed APK and published by
   <https://github.com/openviewmobile/dshd-android/actions/runs/43544880130> at
   <https://github.com/openviewmobile/dshd-android/releases/tag/rootfs-0.3.2>.
4. Later `main` commits update docs, workflow defaults and repository settings;
   they do not change the APK payload above. The `v0.3.2` tag is kept aligned
   with the current public source.

## Verification

```bash
sha256sum dshd-android-release.apk
# aa4e9783e7af547227f9c22970645cfe6daca41b54f11aed814a18b3c7ba3079

apksigner verify --print-certs dshd-android-release.apk
unzip -l dshd-android-release.apk | grep assets/rootfs.tar.xz
unzip -p dshd-android-release.apk assets/rootfs.tar.xz | sha256sum
# 0c91ec900505963afe6cb7e2240bd0161a1d59bd46dc1a6e3a397c7395b18635
```

## Limitations

- The build is reproducible only while the rootfs release asset and signing
  secrets remain available. Keep an offline keystore backup.
- The APK is debug/CI-built before re-signing; the signature was replaced with
  the DSHD release key, but the resulting APK has not yet been installed on a
  physical test device in this release cycle.
- The SBOM at [../sbom.spdx.json](../sbom.spdx.json) is a manual inventory and
  does not replace the license files inside the rootfs.
