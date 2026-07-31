# Changelog

All notable public changes are recorded here. This repository intentionally begins its public history at v1.0.0.

## [1.1.0] - 2026-07-31

### Fixed

- Requested a new destination tab for every Android browser restore intent.
- Replaced misleading load-success language with honest request-dispatch reporting.
- Clarified that installed browser discovery identifies an open target, not a live-tab source.

### Changed

- Reorganized Android navigation into Home, Tabs, Open, Capture, and Settings.
- Replaced decorative gradient/elevated-card styling with a restrained TabDeck utility system.
- Rebuilt Windows Desktop Link as a guided device-to-session-to-selection-to-capture workspace.
- Removed arbitrary collection-count and user-configuration ceilings while retaining protocol security boundaries.
- Added a guided-utility regression validator to CI and release validation.

## [1.0.0] - 2026-07-30

### Added

- Android-first tab inventory using Room, Paging, and Compose.
- Active, Archived, Snoozed, and Trash lifecycle lanes.
- Smart views, ordered launch decks, RE2/J categorization rules, groups, tags, notes, and pinning.
- Database-backed search, sorting, source topology filters, and bounded query-wide bulk controls.
- URL extraction, strict validation, canonicalization, tracker-aware normalization, duplicate clustering, survivor policies, metadata merging, and Trash-based duplicate cleanup.
- Android Sharesheet, deep-link, text/file, backup, Firefox Android, Chromium desktop, and optional Windows ADB/DevTools acquisition routes.
- Short-lived authenticated local bridge API v3 with v1/v2 import-route compatibility.
- Cancellable, paced explicit-browser transfers with partial-result history.
- Full JSON backup format v3 plus grouped Markdown, hardened CSV, and Netscape bookmarks HTML exports.
- Collection-health and quick-control Android widgets.
- Version-synchronized CI, signed tag-release workflow, deterministic connector/source archives, checksums, release manifest, and provenance attestations.

### Security

- Android backup disabled.
- Broad package visibility avoided.
- Bridge disabled by default and limited to explicit loopback/private-network sessions.
- Bounded request sizes and rates, constant-time token comparison, strict URL launch validation, and no hidden cross-app data access.
