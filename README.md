# TabDeck v1 — Android Browser Control Plane

<p align="center"><img src="branding/TabDeck-lockup.svg" alt="TabDeck" width="640"></p>

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

Declared Android transfer targets include Chrome, Chrome Beta, Chrome Dev, Chrome Canary, Firefox, Firefox Nightly, Brave, Brave Beta, and Opera. Other browsers can participate through Android sharing, paste, document import, and ordinary URL handling.

Direct cross-app tab inspection is not a universal Android capability. TabDeck therefore uses explicit user-authorized routes:

1. Firefox Android WebExtension snapshots where the installed Firefox channel supports the required APIs.
2. Android Sharesheet and document import for any browser.
3. Desktop browser extensions that send snapshots to a short-lived authenticated loopback bridge.
4. Optional Windows Desktop Link using user-authorized ADB and a browser-exposed Chromium DevTools socket.

TabDeck does not use root, AccessibilityService scraping, VPN interception, hidden APIs, notification scraping, or private browser database access.

## Repository layout

```text
app/                         Android application
branding/                    Product mark, lockup, and raster release assets
release/                     Public community signing material and documentation
extensions/firefox-android/  Firefox Android development extension
extensions/chromium-desktop/ Chromium-family desktop extension
desktop-link/                Optional Windows-to-Android ADB companion
docs/                        Architecture, operation, security, and release docs
tools/                       Version, validation, test, and packaging tools
.github/workflows/           CI and automatic main-branch release workflows
version.properties           Authoritative public product version
```

## Build locally

Requirements: JDK 17 and Android SDK 36.

```bash
./bootstrap-wrapper.sh
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py
python3 tools/validate_simple_release.py
python3 tools/validate_connector_dom.py
./gradlew clean test lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\bootstrap-wrapper.ps1
python tools\check_version.py
.\gradlew.bat clean test lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Simple GitHub release

No GitHub environment or repository secrets are required. Merge a green version/release change into `main`; the Release workflow derives `v<VERSION_NAME>`, creates or verifies that tag at the merged commit, builds one community-signed APK, and publishes it automatically with the browser connectors, Windows Desktop Link, branding assets, deterministic source archive, validation report, manifest, and SHA-256 checksums. Manual workflow dispatch is retained for recovery.

The community signing key is intentionally public so successive GitHub builds remain upgrade-compatible. Verify authenticity through the official repository, release tag, immutable source commit, manifest, and checksums. See [Build and release](docs/BUILD_AND_RELEASE.md).

## Documentation

- [Installation](docs/INSTALLATION.md)
- [User guide](docs/USER_GUIDE.md)
- [Capability matrix](docs/CAPABILITY_MATRIX.md)
- [Architecture](docs/ARCHITECTURE.md)
- [UX and scope](docs/UX_AND_SCOPE.md)
- [Final implementation record](docs/DEEP_IMPROVEMENT_PLAN.md)
- [Architecture decisions](docs/adr/README.md)
- [Security design](docs/SECURITY.md)
- [Bridge protocol](docs/BRIDGE_PROTOCOL.md)
- [Testing](docs/TEST_PLAN.md)
- [Build verification](docs/BUILD_VERIFICATION.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Release notes](docs/RELEASE_NOTES.md)

## Privacy

Tab data stays local unless the user explicitly shares, exports, or sends it through an enabled local bridge. Android backup is disabled. The live bridge token is excluded from portable backups. No telemetry SDK, cloud account, backend, or application store is required.

## Verification status

The repository contains executable Kotlin core checks, Android unit tests, PowerShell Desktop Link contract tests, static cross-component validation, connector DOM-binding validation, performance-budget validation, secretless-release validation, deterministic packaging, Android lint, and debug plus minified community-release APK assembly. Pull-request CI verifies the community certificate and publishes both APK modes plus all release outputs as one workflow artifact.
