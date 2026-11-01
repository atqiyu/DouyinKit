# Agent build rules

These rules apply to any automated agent or developer producing an APK from this repository.

## Always build the optimized release for device testing and distribution

Use:

```bash
./gradlew :app:assembleRelease
```

Do **not** ship or performance-test the debug APK. Compose/Room in debug mode made the old
list scrolling 90th-percentile frames 69–93 ms; the R8 release build measures about
11–12 ms. The release APK is also about 3.35 MB instead of about 26 MB.

The release build is signed with the standard Android debug keystore on purpose so personal
sideload installs can update previous versions. Never commit a keystore or password.

## Before building

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew :app:testDebugUnitTest :app:lintDebug`.
3. Update `CHANGELOG.md`, `README.md`, release notes and `docs/GITHUB_RELEASE.md`.
4. Build `assembleRelease`, verify the signer, install on a real device, and smoke test:
   parsing, downloads, previews, Library and Downloads scrolling.
5. Copy the APK to `dist/` (ignored by git), record its size and SHA-256 in the release notes,
   then tag and publish.

See [docs/R8_BUILD_REMINDER.md](docs/R8_BUILD_REMINDER.md) for the full checklist and
performance measurement commands, and [docs/GITHUB_RELEASE.md](docs/GITHUB_RELEASE.md) for
the GitHub release workflow.

## Do not regress

- Keep `isMinifyEnabled = true` and `isShrinkResources = true` for release.
- Re-check the ProGuard rules after adding serialization, WebView bridge methods, Room entities
  or reflection-based libraries.
- Do not remove the release signing configuration without discussing an update path; changing
  the signing key prevents installing over an existing app.
- Do not commit `dist/`, keystores, tokens, cookies, `local.properties` or build outputs.
