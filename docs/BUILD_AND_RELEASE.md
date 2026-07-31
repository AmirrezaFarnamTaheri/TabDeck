# Build and release

## Version and toolchain authority

`version.properties` controls the public product version. Internal Room, backup, saved-query, identity, and bridge versions are compatibility contracts and are not reset for branding.

The live release line uses JDK 17, AGP 9.3.1, Gradle 9.6.1, Kotlin Compose 2.4.10, KSP 2.3.10, Android SDK 36, and build-tools 36.0.0. The committed wrapper distribution checksum is authoritative; CI never regenerates wrapper files.

## Local verification

```bash
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py --report build-validation-report.txt
python3 tools/performance_budget.py --validate-config
./gradlew --no-daemon test lintDebug assembleDebug
```

On Windows also run:

```powershell
pwsh -NoLogo -NoProfile -File .\desktop-link\Test-TabDeckLink.ps1
```

## Protected release environment and secrets

Create a GitHub environment named `release`, preferably with required reviewers. Configure these environment secrets:

- `TABDECK_KEYSTORE_BASE64`
- `TABDECK_KEYSTORE_PASSWORD`
- `TABDECK_KEY_ALIAS`
- `TABDECK_KEY_PASSWORD`
- `TABDECK_RELEASE_CERT_SHA256` — the approved signing certificate SHA-256 fingerprint, with or without colons

The workflow fails closed if any value is absent or if the keystore, APK, or AAB fingerprint differs from the approved fingerprint.

## Release process

1. Update version metadata and release notes.
2. Obtain green PR/main CI for the exact source commit.
3. Approve the protected `release` environment.
4. Run the Release workflow with `v<VERSION_NAME>` or push that tag.
5. The workflow binds the run to `git rev-parse HEAD`.
6. Existing tags must already resolve to that commit; manual runs create the tag only after all build and verification gates pass.
7. Signed APK/AAB outputs are verified with `apksigner`, `zipalign`, `jarsigner -verify -strict`, and `keytool` certificate extraction.
8. Both artifacts must match `TABDECK_RELEASE_CERT_SHA256`.
9. `tools/package_release.py` writes schema-v2 provenance containing the source commit, release tag, and signing fingerprint.
10. Checksums and manifest bindings are verified, assets are uploaded and attested, and the GitHub Release is published.

## Deterministic packaging

For an installable release:

```bash
python3 tools/package_release.py \
  --require-android-artifacts \
  --output-dir dist \
  --release-tag v1.0.0 \
  --source-commit <40-char-git-sha> \
  --signing-cert-sha256 <64-hex-fingerprint>
```

Source-only packaging may omit commit/fingerprint, but it is not an installable production release.

## Rollback

Never move or overwrite an existing release tag. Correct the defect, increment `VERSION_CODE`, choose a new semantic version, publish a new tag, and document withdrawal of unsafe artifacts without rewriting history.
