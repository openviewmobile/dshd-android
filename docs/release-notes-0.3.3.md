# DSHD for Android 0.3.3 (DeepSeek Harness Desktop)

This release rebuilds the Android shell as a standard Kotlin project.

## Download

| Asset | Size | SHA-256 |
| --- | --- | --- |
| `dshd-android-release.apk` (embedded runtime, release-signed) | 146 MB | `031fc5a5c6931268f6582f7f3bab0a2de62c9e95cef27f87f5954c0ed0106292` |

The APK is built and signed by GitHub Actions from the sanitized rootfs; no local
APK is uploaded.

## Highlights

- Native Android shell migrated from Java to Kotlin:
  `MainActivity`, `BootOverlay`, `HostKeepAliveService`, `RootfsInstaller`,
  bridges, provider/storage helpers.
- Standard Android layout: root Gradle project + `app/` module,
  `src/main/kotlin`, Kotlin DSL and version catalog.
- AGP 8.13.1, Kotlin 2.2.21, JVM toolchain 17, `versionCode` 6.
- ktlint, detekt and Kotlin unit tests run in CI before the APK build.
- Device upgrade and E2E verified: 4/4.

## Known issues

Unchanged from 0.3.2: native PDF rendering is image-based, host UI is not fully
localized, Android 9+ arm64 only, and the APK is sideloaded.
