# Contributing

Thanks for improving DSHD for Android.

## Development setup

- JDK 17, Android SDK 35, Gradle 8/9.
- Node.js 22+ for helper scripts (the device runtime is Node 24).
- An arm64 Android device/emulator with adb. Root is useful for E2E, but not
  required for normal app use.

```bash
scripts/build-apk.sh
adb install -r app/app/build/outputs/apk/debug/app-debug.apk
scripts/android-e2e.sh
```

## Pull requests

- Keep native UI changes small and test them on a narrow (320–375 CSS px)
  viewport as well as a tablet.
- If you change the rootfs, rebuild the `.tar.xz`, update the pinned SHA-256
  and docs, and state the migration behavior in the PR.
- Run `scripts/check-release-readiness.sh` before a release PR.
- Do not commit `dist/`, `runtime/`, keystores, passwords or provider keys.
- By contributing you agree that your contribution is licensed under
  Apache-2.0 (see `LICENSE`).

## Reporting bugs

Include the APK version, device/Android version, WebView version, steps to
reproduce, and `adb logcat -s dshd:*` output with secrets removed.
