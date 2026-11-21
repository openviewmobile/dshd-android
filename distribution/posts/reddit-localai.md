# r/LocalLLaMA draft

Title: I put a full DeepSeek Harness workspace on an Android phone (no PC)

Body:

This is a sideloaded Android app that embeds a sanitized Linux/Node runtime and
runs the dsh host locally. It is not local LLM inference: you point it at your
own OpenAI-compatible endpoint.

The interesting part for this community is the shape: the phone is the host, not
a thin client. Files, PDF preview, terminal and agent sessions all run against
the local host.

Android 9+/arm64, ~146 MB APK, ~2 GB free for first unpack. Open source,
release-signed, checksums published.

Repo: https://github.com/openviewmobile/dshd-android

I would especially value feedback on the local-host architecture and the
security model around API keys and Android permissions.
