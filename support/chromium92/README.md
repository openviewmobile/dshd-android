# Chromium 92 bundle compatibility patches

The Android WebView on the reference device reports Chromium 92. The upstream
`dsh` web bundles target a newer baseline, so three client bundles are patched
for the embedded rootfs:

| File | Upstream bundle | Changes |
| --- | --- | --- |
| `client.terminal.js` | `dsh-client-ui-sidebar-terminal` | Lower class static blocks. |
| `client.pdf.js` | `dsh-client-ui-sidebar-documentpreview` | Lower class static blocks in the bundle and in the PDF worker string, lower the `v` RegExp flag to `u`, prepend worker polyfills for `Iterator`, `Promise.try`, `toHex`, `getOrInsertComputed`, etc., and route page rendering through the optional `DshdPdf` native bridge. |
| `client.resources.js` | `dsh-client-resources` | Parse `dsh-resource://file/...` without relying on the Chromium 92 `URL` parser, which returns an empty hostname for this custom scheme. |

The main document polyfills live in `app/app/src/main/assets/dsh-index.html` and
are injected into the rootfs frontend by `scripts/patch-rootfs-webview.sh`.

These files are pinned to the `fixed22-nativepdf` rootfs bundle versions. When the `dsh`
version changes, regenerate them from the new bundle and re-run the on-device
terminal/PDF/resource checks. The PDF bundle is Apache-2.0 (PDF.js); see
`NOTICE`.
