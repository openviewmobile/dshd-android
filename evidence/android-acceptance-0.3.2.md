# DSHD for Android 0.3.2 acceptance

Date: 2026-11-10 · Device LGE-AN10, Android 12, arm64-v8a, WebView Chromium 92

## Artifacts

| Artifact | Size | SHA-256 |
| --- | --- | --- |
| `dist/dshd-android-release.apk` (versionCode 5) | 144 MB | `73b7c945590a5dd37475256744046548d61ec403f8aba60c9fa2ea3697be0db5` |
| `dist/dshd-android-release-lite.apk` (no rootfs) | 856 KB | `4e4c4e737d91d0dd27ad6714a2e0a61963ab7b512033f43420edc8573fb3377f` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz` | 143 MB | `4fa3b4fd9bb4bf1877c5738633f3f46e6ba2c6910a1c3384fca08f4ed8caba46` |

The APK was installed over 0.3.1 with the existing release key. The embedded
rootfs SHA-256 changed, so the app replaced the runtime and kept `.dsh`,
Documents, workspace, `.ssh`, `.gnupg` and `.npmrc`.

Re-verified on 2026-11-11 with the final 0.3.2 startup build: release readiness
PASS, `scripts/android-e2e.sh` 4/4 PASS, cold start 9.3–9.5 s and background
resume 0.12 s (see `docs/startup-performance.md`). The startup change only
reduces overlay/JS work; the screen matrix below is unchanged.

## Screen matrix

| Area | Check | Result |
| --- | --- | --- |
| Launch | 0.3.1 → 0.3.2 embedded-rootfs migration, progress, host token, WebView load | PASS |
| Launch overlay | Dark branded screen, staged copy, rootfs percent bar, no white flash / HARNESS flash, fade after composer + history | PASS |
| Native top bar | Provider / Storage / Permissions tabs, active state | PASS |
| Provider | Prefilled URL + model, blank key keeps current key, save restarts host, Back button and Android back | PASS |
| Storage | Internal / external app-specific storage dialog | PASS |
| Permissions | Granted list for 7 capabilities; Android settings shortcut | PASS |
| Permission denial | Revoke microphone → `Grant`; system dialog; grant; phone → in-app high-risk confirmation | PASS (unchanged 0.3.0 code path) |
| Chat | Existing session, markdown response, composer, model/access menus | PASS |
| Left sidebar | Session/workspace list, view options, settings entry | PASS |
| Settings | General, model, plugins, agent presets; bottom of general page scrolls | PASS |
| Right sidebar | Files tab, tab close/plus/split controls, file list | PASS |
| Text preview | `hello.txt` content, refresh, full path wraps on narrow screens | PASS |
| Terminal | `bash` tab, interactive input, `echo TERM-OK` output | PASS |
| PDF preview | Text/embedded-font `test.pdf`: title and both text lines visible; fit-width page image; zoom control shows 50% at the reference viewport and 36% at 320 CSS px | PASS (native Android PdfRenderer) |
| 375 CSS px | All of the above at `wm size 1148x2492`, density 490 | PASS |
| 320 CSS px | Chat, file list, path wrap, PDF text, native tabs usable at `wm size 980x2128` | PASS |
| 320 placeholder | Composer hint renders fully as “Ask or run a task” (Chinese layout uses “输入消息或运行任务”) instead of clipping | PASS |
| APK upgrade | Data/session/files preserved; runtime version replaced | PASS |

## What changed in 0.3.2

### PDF text rendering

Chromium 92's PDF.js worker never delivers the embedded-font dependency, so
text pages stayed blank even after the v6 polyfills. 0.3.2 adds a narrow native
bridge (`PdfBridge.java`, exported as `DshdPdf`):

1. The PDF bytes are stored once in the app cache, then rendered page-by-page by
   Android's `PdfRenderer`, which uses the platform PDF/font engine.
2. The WebView draws the returned PNG into the existing PDF canvas and keeps
   PDF.js only for page count, toolbar and zoom state.
3. If the bridge is unavailable, the existing PDF.js path remains as a fallback.

Verified on the real-device `test.pdf` at normal and 320 CSS px; the text and
embedded font are visible in both. This removes the 0.3.1 PDF known issue.

### Launch experience

The old launch path exposed an empty white WebView and a 12sp status strip while
the host booted. 0.3.2 replaces it with a native `BootOverlay` above the action
bar/WebView:

- dark gradient surface, stars, logo card, DSHD wordmark, “DeepSeek Harness
  Desktop” subtitle and version footer;
- staged copy for unpack, download, host start and workspace open;
- indeterminate bar while the stage is unknown, determinate byte progress for
  embedded/external import; serialized percent updates are throttled by the
  installer;
- Android 12 splash/window background and status/navigation bars use the same
  dark surface;
- the overlay is removed for Provider setup and error states, and fades out
  only after the WebView composer exists and "Loading history" has cleared.
  A 15s safety fallback prevents a permanent cover.

Cold-start time is still dominated by the one-time rootfs unpack and Node/host
boot; the change makes that wait legible rather than hiding it. Warm start
after force-stop measured roughly 7–10s on the test device, with no white
frame and no `HARNESS` loading page visible.

### Other

- Chromium 92 compatibility for `client.terminal.js`, `client.pdf.js` and
  `dsh-client-resources/lib/client.js` (pinned under `support/chromium92/`).
- `dsh-resource://` parser works around Chromium 92 returning an empty host.
- Provider form prefills saved values without quotes, keeps the current key
  when the field is blank, exposes Back, and handles Android back.
- Document preview path wraps at 375/320 CSS px instead of hiding the start.
- 320 CSS px composer placeholder uses a short, fully visible hint.
- Apache-2.0 license, NOTICE, third-party inventory, GitHub CI/templates and
  separate Chinese/English README and demo assets.

## Evidence

New 0.3.2 screenshots are in `evidence/acceptance-0.3.2/`:

- `01-pdf-text-native.png` — text/embedded-font PDF rendered by the native bridge.
- `02-pdf-text-320.png` — same PDF at 320 CSS px.
- `03-320-placeholder.png` — fully visible short composer placeholder.
- `04-chat-normal.png` — normal-width chat after migration.
- `05-startup-overlay.png` — branded launch overlay with staged status and progress.

Earlier 0.3.0/0.3.1 screens remain in `evidence/acceptance-0.3.0/screens/`,
including provider, permissions, terminal, path-wrap and migration evidence.

## Known issues

1. The native PDF path renders page images; the PDF.js text-selection layer is
   intentionally skipped, so selecting/copying text inside a PDF is not available.
2. Very large PDFs are copied to the app cache once and rendered page-by-page;
   first open can take a moment on low-end devices.
3. Very large workspace trees may be slow to enumerate on low-end devices.
4. In-app UI localization is not part of 0.3.2; the separate Chinese/English
   requirement covered by this release is the demo video, not the host UI itself.
5. The launch overlay does not shorten the underlying rootfs/host boot; it
   replaces the blank white wait with branded progress and a controlled reveal.

## Reproduction

```bash
adb install -r dist/dshd-android-release.apk
adb shell am start -n com.dshd.android/.MainActivity
# first launch: wait for the embedded rootfs import
scripts/android-e2e.sh
```
