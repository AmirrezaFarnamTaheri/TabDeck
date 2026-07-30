# TabDeck v1 — Android Browser Control Plane

TabDeck is an Android-first control plane for collecting, reviewing, organizing, deduplicating, archiving, and transferring browser tabs. It treats tabs as durable local data rather than an unbounded browser window list.

The public release history starts at **v1.0.0**. Public product versions are independent from compatibility formats already present in the implementation: Room schema v3, backup format v3, saved-query codec v2, and bridge API v3.

## What TabDeck provides

- Imports URLs from Android sharing, paste, deep links, text-like files, full backups, Firefox Android, desktop browser extensions, and the optional Windows Android companion.
- Maintains a searchable, paged Room inventory with Active, Archived, Snoozed, and Trash states.
- Filters by browser, TabDeck group, source device, source/native group, tags, pinned state, notes, staleness, and lifecycle status.
- Deduplicates by exact URL, normalized URL, or host/path and can merge useful metadata before moving redundant records to Trash.
- Categorizes with ordered RE2/J rules, durable groups, tags, notes, smart views, and reusable ordered launch decks.
- Supports query-wide bounded bulk actions for pinning, tags, groups, lifecycle changes, decks, sharing, copying, and transfer.
- Transfers validated URLs to an explicitly selected installed Android browser through a cancellable, paced queue with partial-result history.
- Exports full JSON backups, grouped Markdown, hardened CSV, and Netscape bookmarks HTML.
- Provides collection-health and quick-control home-screen widgets.

## Supported browser routes

Declared Android transfer targets include Chrome, Chrome Beta, Chrome Dev, Chrome Canary, Firefox, Firefox Nightly, Brave, Brave Beta, and Opera. Other browsers can still participate through Android sharing, paste, document import, and ordinary URL handling.

Direct cross-app tab inspection is not a universal Android capability. TabDeck therefore uses explicit user-authorized routes:

1. Firefox Android WebExtension snapshots where the installed Firefox channel supports the required APIs.
2. Android Sharesheet and document import for any browser.
3. Desktop browser extensions that send snapshots to a short-lived authenticated bridge.
4. Optional Windows Desktop Link using user-authorized ADB and a browser-exposed Chromium DevTools socket.

TabDeck does not use root, AccessibilityService scraping, VPN interception, hidden APIs, notification scraping, or private browser database access.

## Repository layout

```text
app/                         Android application
extensions/firefox-android/  Firefox Android development extension
extensions/chromium-desktop/ Chromium-family desktop extension
desktop-link/                Optional Windows-to-Android ADB companion
docs/                        Architecture, operation, security, and release docs
tools/                       Version, validation, test, and packaging tools
.github/workflows/           CI and signed tag-release workflows
version.properties           Authoritative public product version
```

## Build locally

Requirements: JDK 17 and Android SDK 36.

```bash
./bootstrap-wrapper.sh
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py
./gradlew clean test lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\bootstrap-wrapper.ps1
python tools\check_version.py
.\gradlew.bat clean test lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Release

A tag-driven GitHub Actions workflow builds and verifies a signed APK and AAB, packages all connectors and source archives deterministically, produces SHA-256 checksums and a machine-readable manifest, creates provenance attestations, uploads workflow artifacts, and publishes a GitHub Release.

See [Build and release](docs/BUILD_AND_RELEASE.md) for required repository secrets and the exact release procedure.

## Documentation

- [Installation](docs/INSTALLATION.md)
- [User guide](docs/USER_GUIDE.md)
- [Capability matrix](docs/CAPABILITY_MATRIX.md)
- [Architecture](docs/ARCHITECTURE.md)
- [UX and scope](docs/UX_AND_SCOPE.md)
- [Security design](docs/SECURITY.md)
- [Bridge protocol](docs/BRIDGE_PROTOCOL.md)
- [Testing](docs/TEST_PLAN.md)
- [Build verification](docs/BUILD_VERIFICATION.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Release notes](docs/RELEASE_NOTES.md)

## Privacy

Tab data stays local unless the user explicitly shares, exports, or sends it through an enabled local bridge. Android backup is disabled. The live bridge token is excluded from portable backups. No telemetry SDK or cloud account is required.

## Verification status

The repository contains a pure Kotlin executable core harness and a static cross-component validator. Pull-request CI performs the full Android unit-test, lint, and debug-build gates. Tagged release CI additionally builds and verifies signed release artifacts before publication. See [Build verification](docs/BUILD_VERIFICATION.md) for the distinction between locally observed and CI-enforced evidence.
