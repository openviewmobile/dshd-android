# Changelog

## 0.3.2 — 2026-11-10

- Fix blank text/embedded-font PDF pages with a native Android `PdfRenderer`
  bridge; keep PDF.js for page count and zoom, with the old path as fallback.
- Fix the 320 CSS px composer placeholder with a short, fully visible hint.
- Replace the white/flashy launch with a branded dark boot overlay, staged
  status copy, rootfs byte progress and a smooth reveal after the WebUI is ready;
  add a matching Android 12 splash/window background and dark native tabs.
- Bump the embedded rootfs to `fixed22-nativepdf` (143 MB, 144 MB APK,
  versionCode 5).
- Replace the mixed-language demo with separate Chinese-only and English-only
  three.js motion videos and neural narration.
- Name the product explicitly: DSHD = **DeepSeek Harness Desktop**; the launch
  screen subtitle now shows the full name.
- Reduce boot-overlay redraw work, start readiness probing at first paint, and
  document cold/warm startup timings in `docs/startup-performance.md`.
- Add 16:9 English/Chinese YouTube promos, thumbnails and upload metadata.

## 0.3.1 — 2026-11-10

- Embed the patched `fixed21-compat` rootfs by default (140 MB APK).
- Fix Chromium 92 compatibility for the terminal, document/PDF preview and
  `dsh-resource://` file addresses.
- Provider settings: keep the current API key when the field is blank,
  prefill the saved base URL/model, add a visible Back action and Android
  back handling; strip accidental quotes from saved values.
- Make the narrow-screen document path wrap instead of hiding its beginning.
- Add Apache-2.0 license, NOTICE, third-party inventory and GitHub templates.

## 0.3.0 — 2026-11-10

- Embed the rootfs in the APK; import on first launch.
- Preserve `.dsh`, Documents, workspace, SSH/GPG and npm config across APK
  rootfs upgrades; version the embedded image by SHA-256.
- Add the small `DSHD_EMBED_ROOTFS=none` APK variant.
- Fix attachment chooser, Android bridge paths and mobile settings layout.

## 0.2.x

- Native Android shell, Cortex/Node/dsh host, first-run provider setup,
  runtime permissions with high-risk confirmation, storage switching,
  E2E harness and release checks.
