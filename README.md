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

Direct cross-app tab inspection is not a universal Android capability. TabDeck therefore uses explicit user-authorized routes.

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

## Documentation

- [Installation](docs/INSTALLATION.md)
- [User guide](docs/USER_GUIDE.md)
- [Capability matrix](docs/CAPABILITY_MATRIX.md)
- [Architecture](docs/ARCHITECTURE.md)
- [UX and scope](docs/UX_AND_SCOPE.md)
- [Deep improvement plan](docs/DEEP_IMPROVEMENT_PLAN.md) — delivery roadmap, architecture invariants, and acceptance criteria
- [Security design](docs/SECURITY.md)
- [Bridge protocol](docs/BRIDGE_PROTOCOL.md)
- [Testing](docs/TEST_PLAN.md)
- [Build verification](docs/BUILD_VERIFICATION.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Release notes](docs/RELEASE_NOTES.md)
