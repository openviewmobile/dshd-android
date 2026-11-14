# GitHub repository settings

Repository: <https://github.com/openviewmobile/dshd-android>

The code side is complete; these settings are repository **administrative**
actions and are not stored in Git. Run them once from a maintainer account.

## About

- Description: `DeepSeek Harness Desktop for Android - a local AI workstation in your pocket.`
- Website: optional (YouTube/release page).
- Topics: `android`, `deepseek`, `deepseek-harness`, `local-ai`, `ai-workstation`,
  `webview`, `arm64`, `open-source`.
- Social preview: upload [`docs/assets/social-preview.png`](assets/social-preview.png)
  (1280×640 PNG).

## Code security and analysis

Open **Settings → Code security and analysis** and enable:

- Dependabot alerts;
- Dependabot security updates;
- Private vulnerability reporting;
- Secret scanning and push protection (public repositories);
- CodeQL/code scanning if available for Java.

## Branches / rulesets

Protect `main`:

- require a pull request before merging;
- require the `Android APK` status check to pass;
- block force pushes and deletions;
- allow administrators to bypass only when necessary.

The release workflow creates tags/releases; do not let pull requests create them.

## Releases

- `v0.3.2` is the production-signed release.
- `rootfs-0.3.2` contains the sanitized runtime source for reproducible builds.
- Old `v0.3.2-full-debug` was removed.
- Never upload a local APK; use **Actions → Full APK release**.

## Why these are manual

GitHub's repository settings/topics/social-preview endpoints require repo
administration and a browser session or an admin token. The publishing session
used for this project could create commits and releases, but GitHub rejected
automated admin form posts. No admin token is stored in the repository.
