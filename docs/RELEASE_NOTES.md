# TabDeck v1.2.0 release notes

TabDeck v1.2.0 adds local automation and operational visibility without introducing a cloud account or remote telemetry.

## Automation and recovery

- A unique daily WorkManager task restores due snoozed tabs and removes Trash older than the configured retention period.
- Work is constrained when the battery or device storage is low, uses bounded exponential retry, and is idempotently replaced rather than duplicated.
- Settings can enable or disable automatic maintenance, set retention days, and run maintenance immediately.
- The most recent maintenance outcome is stored locally and shown in Settings and the activity widget.

## Widgets

- **Deck Launcher** opens a saved deck directly in the browser-target workflow.
- **Transfer Status** shows recent dispatched open requests and maintenance health.
- Existing collection-health and quick-capture widgets now refresh together with the new widgets after relevant state changes.

## Compatibility

- Room schema: v3
- Backup format: v3, with backward-compatible maintenance settings
- Saved-query codec: v2
- Bridge API: v3 with v1/v2 route compatibility

Install `TabDeck-v1.2.0.apk` from the matching GitHub Release and verify it against the release checksums and certificate fingerprint.
