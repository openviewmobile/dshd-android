# Security policy

## Supported versions

Security fixes are made on the latest release line. Please test with the
newest APK before reporting.

## Reporting

Do not open a public issue for a vulnerability. Send a private report to the
maintainers listed in the repository profile, including:

- affected version and device,
- a minimal reproduction,
- impact (local app data, provider key, rootfs, network),
- relevant logs with tokens and API keys removed.

## Sensitive data

- Provider keys are stored in Android Keystore-backed storage; never paste a
  key into an issue or screenshot.
- The release keystore lives outside the repository (`~/.dshd/`); losing it
  prevents upgrade-compatible releases.
- The app exposes a localhost-only host. Do not bind it to a public interface.
