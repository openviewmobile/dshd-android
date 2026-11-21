# Distribution and store listings

This page tracks where DSHD is published and what is ready for submission.
Canonical download:

<https://github.com/openviewmobile/dshd-android/releases/latest/download/dshd-android-release.apk>

## Current channels

| Channel | Status | Notes |
| --- | --- | --- |
| GitHub Releases | Published | Production-signed APK, `.sha256`, release notes |
| Obtainium | Available by URL | No submission; users add `https://github.com/openviewmobile/dshd-android` |
| APKMirror | Ready to submit | Requires maintainer account and review |
| APKPure | Ready to submit | Requires developer account |
| Aptoide | Ready to submit | Requires developer account |
| Uptodown | Ready to submit | Editorial review |
| IzzyOnDroid | Ready to request | Third-party F-Droid repo; large embedded rootfs may be rejected |
| F-Droid official | Not eligible | Requires source-built artifacts; the embedded rootfs is prebuilt |
| Google Play | Not eligible now | targetSdk 28, 146 MB APK and embedded runtime need policy/build changes |
| Chinese app stores | Deferred | Developer identity, software copyright/filing and target API requirements |

Store accounts, captchas and legal agreements cannot be completed by the
repository CI. The metadata and assets below are the submission pack.

## Obtainium

1. Install Obtainium.
2. Add app → `https://github.com/openviewmobile/dshd-android`.
3. Select GitHub Releases.
4. Keep the APK filter matching `dshd-android-release.apk`.

## Submission pack

| Item | Location |
| --- | --- |
| English metadata | `fastlane/metadata/android/en-US/` |
| Chinese metadata | `fastlane/metadata/android/zh-CN/` |
| App icon 512×512 | `fastlane/metadata/android/*/images/icon.png` |
| Feature graphic 1024×500 | `fastlane/metadata/android/*/images/featureGraphic.png` |
| Phone screenshots | `fastlane/metadata/android/*/images/phoneScreenshots/` |
| Privacy policy | [`../PRIVACY.md`](../PRIVACY.md) |
| Security policy | [`../SECURITY.md`](../SECURITY.md) |
| Checksums | GitHub Release `.sha256` asset |

## Copy-paste short description

**English**

> Run a DeepSeek Harness workspace on your phone. No PC required.

**中文**

> 在 Android 手机上运行完整 DeepSeek Harness 工作区，无需电脑。

## Required disclosures

Always state these clearly:

- Android 9+ (API 28), arm64-v8a only.
- APK about 146 MB; first unpack needs about 2 GB free.
- Sideloaded; not a Google Play app.
- The dsh host runs locally, but model inference goes to the user's configured
  OpenAI-compatible endpoint.
- targetSdk 28 is intentional for the glibc rootfs and Cortex runtime.
- The APK is release-signed; verify the SHA-256 before installing.

## Do not claim

- “Runs an LLM fully offline on the phone.”
- “No network required.”
- “Google Play / F-Droid official.”
- “Works on every Android phone.”

## Next external actions

These require a human maintainer account:

1. Submit to APKMirror, APKPure, Aptoide and Uptodown.
2. Apply to IzzyOnDroid with the GitHub release URL.
3. Submit PRs to Awesome Selfhosted, Awesome Local AI, Awesome Android and
   Awesome Kotlin.
4. Publish the YouTube demo and Product Hunt listing.
5. Post Show HN / Reddit / V2EX using the drafts in `distribution/posts/`.
