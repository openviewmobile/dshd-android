# DSHD for Android — Privacy Policy

Last updated: 2026-11-21

DSHD is an open-source Android shell for a locally running DeepSeek Harness
host. This policy describes the production APK published at
<https://github.com/openviewmobile/dshd-android/releases>.

## Short version

- DSHD has no account system and no DSHD-operated cloud backend.
- The app runs its Linux/Node/dsh host on the phone.
- Prompts, attachments and file context are sent only to the model endpoint
  **you configure**.
- API keys are stored in Android Keystore-backed app-private storage.
- Uninstalling the app deletes its app-private data.

## Data the app processes

| Data | Where it is stored/processed | Why |
| --- | --- | --- |
| Model base URL, model id | App-private configuration | Connect to your endpoint |
| API key | Encrypted with an Android Keystore key | Authenticate to your endpoint |
| Chat/session content, attachments | App-private storage | Local workspace history |
| Files you select | App-private cache or the storage location you choose | Preview/attach to a task |
| Android permission state | Android/app state | Only grant the capabilities you enable |

The embedded runtime is unpacked into app-private storage on first launch.

## Network traffic

- The embedded APK does not download its rootfs.
- The app connects to the HTTPS model endpoint you enter. The endpoint receives
  the prompts, attachments and context you submit, subject to that provider's
  privacy policy.
- If you install a non-embedded/lite build, the app may download the runtime
  from the URL configured at build time.
- The app does not include a DSHD-operated analytics or advertising SDK.

## Permissions

Permissions are requested individually. High-risk capabilities (phone, contacts,
location, nearby Bluetooth) require an additional in-app confirmation. Denying
a permission only disables that capability.

## Children

DSHD is a developer tool and is not directed at children under 13.

## Changes

Changes to this policy will be committed to this repository with an updated
date. The canonical version is:
<https://github.com/openviewmobile/dshd-android/blob/main/PRIVACY.md>

## Contact

Security reports: see [SECURITY.md](SECURITY.md).  
General questions: open an issue at
<https://github.com/openviewmobile/dshd-android/issues>.
