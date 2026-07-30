# TabDeck v1.0.0 release notes

TabDeck v1.0.0 is the first public release of the Android-first browser-tab control plane.

## Highlights

- A local, paged tab inventory independent of any single browser.
- Safe acquisition through Android sharing, files, Firefox Android, desktop extensions, and an optional Windows-to-Android companion.
- Search, source topology, groups, tags, notes, pinning, smart views, and reusable ordered launch decks.
- Previewed duplicate analysis with deterministic survivor policies and recoverable Trash-based cleanup.
- Ordered RE2/J categorization rules that avoid backtracking-regex failure modes.
- Bounded query-wide bulk operations for libraries up to the implemented 25,000-item control ceiling.
- Explicit Android-browser transfer with destination confirmation, pacing, cancellation, and partial-result history.
- Portable backup format v3 plus Markdown, CSV, and bookmarks HTML exports.
- Two Android home-screen widgets and adaptive phone/tablet navigation.

## Compatibility identifiers

These identifiers are implementation formats and are intentionally not reset with the public product version:

- Room schema: v3
- Backup format: v3
- Saved-query codec: v2
- Bridge API: v3, with v1/v2 import-route compatibility

## Important limitation

Android does not provide a general API that lets one ordinary application read or close every tab in unrelated browser applications. TabDeck uses only explicit user-authorized connectors and transfer routes. Browser-native group creation and universal source-tab closure are therefore not promised.

## Installation

Install the signed `TabDeck-v1.0.0.apk` from the GitHub Release, or build from source. The AAB is intended for store/distribution workflows and is not directly installed by users. Connector installation is optional and documented in `docs/INSTALLATION.md`.
