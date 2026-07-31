# Build and release

## Version and toolchain authority

`version.properties` controls the public product version. Internal Room, backup, saved-query, identity, and bridge versions are compatibility contracts and are not reset for branding.

## Local verification

First-time setup:

```bash
./bootstrap-wrapper.sh
```

Verification:

```bash
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py --report build-validation-report.txt
python3 tools/performance_budget.py --validate-config
./gradlew --no-daemon test lintDebug assembleDebug
```

A clean rebuild may be used when diagnosing stale local build state:

```bash
./gradlew clean test lintDebug assembleDebug
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
- `TABDECK_RELEASE_CERT_SHA256`
