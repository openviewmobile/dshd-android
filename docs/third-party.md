# Third-party components and licenses

This repository's own code is Apache-2.0. The release APK also contains a
prebuilt runtime image, so redistributing the APK means redistributing those
components too. This page is the inventory; it is not legal advice.

| Component | Version / source | License | Notes |
| --- | --- | --- | --- |
| DSHD Android shell | this repository | Apache-2.0 | Java, Gradle, scripts, docs. |
| DeepSeek Harness (`dsh`) | npm `@deepseek-ai/dsh` | MIT | Web host and client plugins. |
| Cortex | Cortex v1.26.50 | Apache-2.0 | glibc userland launcher and `libcortex-hook.so`. |
| Node.js | official linux-arm64 v24.22.2 | MIT | Includes bundled third-party notices in the Node distribution. |
| Ubuntu 24.04 packages | Ubuntu archive | mixed (GPL/LGPL/MIT/BSD/Apache) | Rootfs contains binaries and libraries; keep the image's copyright files. |
| PDF.js / pdfjs-dist | 6.3.289 | Apache-2.0 | Patched for Chromium 92; see `support/chromium92/`. |
| xterm.js and addons | 6.0.0 / 0.11.0 | MIT | Bundled inside the terminal client patch. |
| AndroidX / XZ | see Gradle dependencies | Apache-2.0 | `org.tukaani:xz:1.10` is public-domain / 0BSD-style. |

## Demo tooling (not shipped in the APK)

| Component | Version / source | License | Notes |
| --- | --- | --- | --- |
| three.js | npm `three@0.187.0` | MIT | Scene, shaders and transitions in `demo/`. |
| Playwright | npm `playwright-core@1.65.0` | Apache-2.0 | Drives local Chrome for deterministic frame capture. |

## Redistribution checklist

1. Ship `LICENSE` and `NOTICE` with source and binary distributions.
2. Do not remove copyright headers from patched JavaScript bundles.
3. For the Ubuntu userland, preserve `/usr/share/doc/*/copyright` in the
   rootfs image, and provide source for GPL/LGPL binaries on request or via
   the Ubuntu source archive.
4. The release keystore and provider API key are **not** part of the open
   source release.
