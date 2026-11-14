# DSHD for Android 0.3.2 (DeepSeek Harness Desktop)

First-use and 16:9 promo media are generated locally (see `demo/README.md` and
`docs/youtube-promo.md`); large video files are not stored in Git.

## Download

The production-signed full APK is attached to the [0.3.2 release](https://github.com/openviewmobile/dshd-android/releases/tag/v0.3.2).

| Asset | Size | SHA-256 |
| --- | --- | --- |
| `dshd-android-release.apk` (embedded runtime, release-signed) | 144 MB | `aa4e9783e7af547227f9c22970645cfe6daca41b54f11aed814a18b3c7ba3079` |
| `dshd-android-release.apk.sha256` | 91 B | — |

The APK was built by GitHub Actions from the sanitized rootfs and re-signed
with the DSHD release key. No local APK was uploaded.

The embedded rootfs is SHA-256 versioned. Upgrading from 0.3.1 replaces the
runtime and keeps `.dsh`, Documents, workspace, SSH/GPG keys and `.npmrc`.

## Highlights

- **Launch experience rebuilt**: a dark branded overlay reports unpack,
  host-start and workspace stages with real byte progress; it fades only after
  the composer/history is ready. Android 12 splash, window background and
  native tabs now match the same dark surface.
- **PDF text rendering fixed** with a narrow Android `PdfRenderer` bridge:
  text and embedded-font PDFs now show their page content on Chromium 92.
- **320 CSS px composer** uses a short, fully visible placeholder.
- Chromium 92 fixes: terminal, document/PDF shell, `dsh-resource://` files,
  PDF worker polyfills.
- Provider settings prefill cleanly, keep the current key when left blank,
  and provide Back / Android back.
- File preview paths wrap instead of hiding the start on 375/320 CSS px.
- Apache-2.0 license, NOTICE, third-party inventory, CI and GitHub templates.
- Separate Chinese-only and English-only demo videos with neural narration.
- Startup work: remove full-screen boot-overlay animation work, start readiness
  probing at first paint, and document the real cold-start budget. The Node
  host still dominates cold start; warm resume is unchanged and fast.
- 16:9 English/Chinese YouTube promos, thumbnails and upload metadata.

## Known issues

- PDF pages are rendered as images by the native bridge; selecting/copying
  text inside a PDF is not available.
- Large PDFs are copied once into the app cache and rendered page-by-page.
- The host UI is not fully localized; the separate-language guarantee applies
  to the demo videos.

Full acceptance report: [evidence/android-acceptance-0.3.2.md](../evidence/android-acceptance-0.3.2.md).
Launch screenshot: [05-startup-overlay.png](../evidence/acceptance-0.3.2/05-startup-overlay.png).
