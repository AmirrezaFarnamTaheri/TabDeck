# Build and release

## Version authority

`version.properties` controls the public product version. Room, backup, saved-query, identity, and bridge versions are compatibility contracts and are not reset for branding.

## Local verification

```bash
./bootstrap-wrapper.sh
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py --report build-validation-report.txt
python3 tools/validate_simple_release.py
python3 tools/performance_budget.py --validate-config
./gradlew --no-daemon test lintDebug assembleDebug
```

On Windows also run:

```powershell
pwsh -NoLogo -NoProfile -File .\desktop-link\Test-TabDeckLink.ps1
```

## Release model

TabDeck publishes a simple GitHub Release. It does not require a protected environment, GitHub Actions secrets, application-store configuration, or an AAB.

The release workflow builds one minified release APK using the repository-published community signing key in `release/tabdeck-community.jks.base64`. The key is intentionally public and exists only to keep Android package signatures stable across community releases. It does not prove exclusive publisher identity.

Trust a build by checking all of the following:

1. it appears on the official GitHub repository;
2. the release tag resolves to the source commit recorded in the manifest;
3. the APK and archives match `TabDeck-v*-SHA256.txt`;
4. the APK certificate is `8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`.

## Create a release

1. Update `version.properties`, `CHANGELOG.md`, and `docs/RELEASE_NOTES.md`.
2. Run the local verification commands.
3. Merge the verified changes to `main`.
4. Push an annotated tag matching the version:

```bash
git tag -a v1.0.0 -m "TabDeck v1.0.0"
git push origin v1.0.0
```

Alternatively, run the **Release** workflow manually and provide the version tag. If the tag does not exist, the workflow creates it at the dispatched commit.

## Published assets

- `TabDeck-v1.0.0.apk`
- `TabDeck-v1.0.0-Firefox-Bridge-unsigned.xpi`
- `TabDeck-v1.0.0-Chromium-Bridge.zip`
- `TabDeck-v1.0.0-Desktop-Link.zip`
- `TabDeck-v1.0.0-Branding.zip`
- `TabDeck-v1.0.0-source.zip`
- validation report
- release manifest
- SHA-256 checksums
- optional R8 mapping file

## Rollback

Do not replace an existing tag with different code. Correct the defect, increment `VERSION_CODE`, choose a new semantic version, and publish a new tag. Remove an unsafe APK from the release page only when necessary, and document the withdrawal without rewriting repository history.
