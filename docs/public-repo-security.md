# Public-repository security and privacy audit

Scope: the standalone `dshd-android` tree intended for GitHub publication, version
0.3.2. This is a source-code and media audit; it does not replace a formal
third-party security review.

## Publication model

- **Only source, docs, tests and small thumbnails are published.** `dist/`,
  rootfs archives, APKs, Gradle outputs, generated demo frames and the release
  keystore are gitignored. Large GIF/MP4 demo files are generated locally and
  are not part of the public tree.
- **No APK from the local `dist/` directory is uploaded.** GitHub Actions builds
  a lite debug APK from source and uploads it as a workflow artifact.
- The public branch starts with a fresh root commit. The local pre-publication
  history is kept only in the local `archive/pre-public` branch and is not
  pushed.

## Checks performed

- Full tracked-tree scan for private keys, cloud/API tokens, GitHub tokens,
  `password`/`secret`/`token` assignments, private LAN IPs and local user paths.
- Git object/history scan for the same patterns. It found an old local
  `dsh web` token in a historical evidence log and private LAN addresses in
  evidence files. These are not in the public root commit.
- Tracked file scan for ``<local-user-path>``, personal e-mail addresses in content,
  and the local account name.
- Image review of provider/setup/chat screenshots found no API key, bearer
  token, account name or e-mail. The public tree keeps only the five 0.3.2
  launch/PDF screenshots; earlier per-screen screenshots were removed.
- Commit identity changed to the GitHub noreply address
  `aiman2050@users.noreply.github.com` for the public history.
- No keystore, `*.password`, `.env`, `.npmrc` with credentials, or `dist/`
  artifact is tracked.

## Sanitized test data

Private LAN addresses used during development were replaced with RFC 5737
documentation addresses (`192.0.2.0/24`), and local macOS paths with
`<workspace>`. Historical local tokens are not part of the public root commit.

## Residual risks

- The repository contains deliberate **text evidence** and five 0.3.2
  screenshots from a physical test device. It does not contain credentials, but
  it does reveal the device model and that the author tested a model endpoint.
  Remove `evidence/` if that metadata is not acceptable.
- Demo media contains the product UI and generic test file names; no personal
  documents are included.
- The app talks to a user-configured OpenAI-compatible endpoint. The README and
  in-app copy must keep stating that runtime/files are local while prompts and
  attached context go to that endpoint.
- GitHub Actions has `contents: read`; it cannot publish a release or modify the
  repository. Release signing, if added later, must use repository secrets and
  must never commit the keystore.
- The full embedded-rootfs APK is not built by the default workflow because the
  143 MB rootfs is not stored in Git. The default artifact is a **lite debug
  APK**; a production/full build still requires the rootfs asset and release
  signing secrets.

## Before making the repository public

- [ ] Create the empty GitHub repository and push the clean root commit.
- [ ] Verify the Actions run produces `app-debug.apk` + `SHA256SUMS` as an
  artifact.
- [ ] Keep `archive/pre-public` local; do not push it.
- [ ] Enable secret scanning/push protection and branch protection.
- [ ] Do not upload `$HOME/.dshd/android-release.jks` or its password.
- [ ] Back up the release keystore offline before publishing an upgradeable
  production release.
