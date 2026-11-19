# CI and releases

`.github/workflows/android.yml` runs module/app unit tests, Debug Lint and
instrumented tests on API 24 and API 34 x86_64 AOSP emulators. The physical Google TV
validation additionally covers ARM libmpv; the emulator's MPV test skips unsupported
ABIs. PRs run checks without signing secrets or publishing.

After every successful master push (or manual workflow run), CI builds an R8
optimized, signed universal Release APK with ARMv7, ARM64 and x86_64 support, plus
SHA256 checksums, and publishes a uniquely named nightly prerelease. `v*` tags
publish normal releases only after the same checks pass. Reports and APKs also
remain available as Actions artifacts. Version codes use 10000 + workflow run
number, so subsequent CI APKs can upgrade in place.

Repository Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. These are configured for this repository. The dedicated release
key and its credentials are backed up outside the repository at
`~/.config/ohmytv/release-signing/` on the maintainer's machine (private permissions).
Keep this directory backed up: future APK updates must use the same key. Do not
commit keystores or passwords. The CI signing key is removed from the runner even
on failure. A release APK uses a different certificate from locally installed
Debug APKs, so Android will not allow replacing a Debug installation with it.

A dedicated release certificate is configured in repository Actions secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Its local backup
is outside Git at `~/.config/ohmytv/release-signing/`; preserve this backup for future
updates. Release and developer Debug APKs have different signing certificates;
only APKs signed by the same certificate can update one another in place.
