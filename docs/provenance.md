# Build and release provenance

This page records how the public 0.3.3 APK was produced. All artifacts were
built and signed by GitHub Actions; no local APK was uploaded.

## Production APK

| Item | Value |
| --- | --- |
| Release | <https://github.com/openviewmobile/dshd-android/releases/tag/v0.3.3> |
| Asset | `dshd-android-release.apk` |
| Size | 152,755,693 bytes |
| SHA-256 | `031fc5a5c6931268f6582f7f3bab0a2de62c9e95cef27f87f5954c0ed0106292` |
| Signing certificate | DSHD Project Release, SHA-256 `1C:98:8D:F8:9D:62:51:A1:B6:C8:AA:1D:74:C3:5F:4C:39:4A:0E:C0:CF:52:A9:4A:A9:B6:AD:F3:EF:4D:36:EC` |
| Embedded rootfs SHA-256 | `0c91ec900505963afe6cb7e2240bd0161a1d59bd46dc1a6e3a397c7395b18635` |

## Source and build chain

1. The **full APK** was built and signed by the `Full APK release` workflow at
   <https://github.com/openviewmobile/dshd-android/actions/runs/44850733394>
   from commit `bc64a7c63c6b2be93596c7092cf2d3b5ee035ec9` (tag `v0.3.3`).
2. It downloaded the sanitized rootfs from
   <https://github.com/openviewmobile/dshd-android/releases/tag/rootfs-0.3.2>
   (SHA-256 `0c91...`), built `assembleRelease` with the embedded rootfs, and
   signed it with the release keystore from GitHub Actions Secrets.
3. The production-signed APK and its SHA-256 file were uploaded to the
   `v0.3.3` Release. The same APK was downloaded to a physical LGE-AN10
   (Android 12, arm64) and passed the 4/4 device E2E.
4. Later documentation commits do not change the APK payload. The `v0.3.3`
   tag is kept aligned with the current public source.

## Verification

```bash
sha256sum dshd-android-release.apk
# 031fc5a5c6931268f6582f7f3bab0a2de62c9e95cef27f87f5954c0ed0106292

apksigner verify --print-certs dshd-android-release.apk
unzip -l dshd-android-release.apk | grep assets/rootfs.tar.xz
unzip -p dshd-android-release.apk assets/rootfs.tar.xz | sha256sum
# 0c91ec900505963afe6cb7e2240bd0161a1d59bd46dc1a6e3a397c7395b18635
```

## Reproducibility and limitations

- The full build is reproducible from the `rootfs-0.3.2` asset and the
  repository secrets while both remain available.
- Keep an offline backup of the release keystore; losing it prevents future
  upgrades.
- The SBOM at [../sbom.spdx.json](../sbom.spdx.json) is a manual inventory and
  does not replace the license files inside the rootfs.
