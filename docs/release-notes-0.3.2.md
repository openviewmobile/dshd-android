# DSHD for Android 0.3.2 (DeepSeek Harness Desktop)

First-use and 16:9 promo media are generated locally (see `demo/README.md` and
`docs/youtube-promo.md`); large video files are not stored in Git.

## Download

| Asset | Size | SHA-256 |
| --- | --- | --- |
| `dshd-android-release.apk` (embedded runtime) | 144 MB | `73b7c945590a5dd37475256744046548d61ec403f8aba60c9fa2ea3697be0db5` |
| `dshd-android-release-lite.apk` (no runtime) | 856 KB | `4e4c4e737d91d0dd27ad6714a2e0a61963ab7b512033f43420edc8573fb3377f` |

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
