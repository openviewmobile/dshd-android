# Show HN draft

Title:

Show HN: DSHD – A DeepSeek Harness workspace on an Android phone

Body:

I wanted to use DeepSeek Harness on my phone without carrying a laptop or
SSHing into a PC. So I embedded a sanitized Ubuntu/Node runtime into an Android
APK, built a native Kotlin shell around it, and start the dsh host as a
foreground service.

What works today:
- files, PDF preview, terminal and agent sessions;
- bring-your-own OpenAI-compatible endpoint;
- API keys in Android Keystore-backed storage;
- no PC, root or Termux.

Important limitations:
- Android 9+, arm64 only;
- ~146 MB APK and ~2 GB first unpack;
- sideloaded, not Play Store;
- model inference is remote to your endpoint, not local LLM inference;
- targetSdk 28 is intentional for the glibc runtime.

GitHub: https://github.com/openviewmobile/dshd-android
Release: https://github.com/openviewmobile/dshd-android/releases/latest
Demo: <YouTube link>

Happy to answer questions about embedding the rootfs, permissions, or CI
signing.
