# Build and release

## Version authority

`version.properties` is the public version source of truth:

```properties
VERSION_NAME=1.0.0
VERSION_CODE=1
RELEASE_DATE=2026-07-30
```

`tools/check_version.py` verifies that Android and both extension manifests agree. Internal Room, backup, saved-query, and bridge versions are compatibility contracts and must not be reset for branding.

## Local verification

```bash
./bootstrap-wrapper.sh
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py
./gradlew clean test lintDebug assembleDebug
```

The bootstrap script downloads the Gradle 9.1.0 wrapper JAR and verifies it against the checksum pinned in the script. The wrapper distribution itself is checksum-pinned in `gradle-wrapper.properties`.

## CI workflow

`.github/workflows/ci.yml` runs on pushes, pull requests, and manual dispatch. It:

1. installs JDK 17 and Gradle 9.1.0;
2. installs Android SDK platform 36 and build-tools 36.0.0;
3. generates and verifies the wrapper JAR when it is not committed;
4. checks synchronized versions;
5. runs the executable core harness and static validator;
6. runs Android unit tests, lint, and a debug build;
7. uploads the debug APK and reports.

## Release secrets

Configure these GitHub Actions secrets:

- `TABDECK_KEYSTORE_BASE64` — base64-encoded Android keystore bytes.
- `TABDECK_KEYSTORE_PASSWORD`
- `TABDECK_KEY_ALIAS`
- `TABDECK_KEY_PASSWORD`

The workflow writes the keystore only into the runner's temporary directory and deletes it after use. Never commit a keystore or signing password.

## Create a release

1. Update `version.properties`, `CHANGELOG.md`, and `docs/RELEASE_NOTES.md`.
2. Run all local checks.
3. Commit the release state.
4. Create an annotated or signed tag exactly matching `v<VERSION_NAME>`:

```bash
git tag -s v1.0.0 -m "TabDeck v1.0.0"
git push origin v1.0.0
```

The release workflow rejects a tag that disagrees with `version.properties` or the extension manifests.

## Release workflow outputs

The tag workflow builds and verifies:

- `TabDeck-v1.0.0.apk`
- `TabDeck-v1.0.0.aab`
- optional R8 mapping output
- Android Studio source archive
- Firefox unsigned development XPI
- Chromium desktop extension ZIP
- Windows Desktop Link ZIP
- validation report
- SHA-256 checksum file
- machine-readable release manifest

It uploads a workflow artifact, creates provenance attestations, and publishes the matching GitHub Release using `docs/RELEASE_NOTES.md`.

## Manual deterministic packaging

After a successful Android release build:

```bash
python3 tools/package_release.py --require-android-artifacts --output-dir dist
```

Without `--require-android-artifacts`, the command packages source/connectors and records that Android binaries were not present. This mode is useful for source-only validation but is not a final installable release.

## Rollback

Do not overwrite an existing release tag or asset set. Correct the defect, increment `VERSION_CODE`, choose a new semantic version, update release notes, and publish a new tag. If a published binary is unsafe, remove it from distribution and document the withdrawal without rewriting repository history.
