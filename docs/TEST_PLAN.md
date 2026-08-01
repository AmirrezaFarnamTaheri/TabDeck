# TabDeck v1 release test plan

## A. Import and parsing

- Empty, whitespace-only, malformed, future-version, and unrelated JSON files.
- 16 MiB exact boundary and one-byte-over boundary.
- UTF-8 truncation around ASCII, 2-byte, 3-byte, emoji/surrogate pairs, and malformed surrogate input.
- large shared-item collections and duplicate ClipData/stream URIs without count truncation.
- 25,001+ URL imports preserve every valid candidate; request-byte overflow is rejected explicitly rather than partially accepted.
- Markdown, nested/balanced parentheses, punctuation, HTML entities, and `www.` URLs.
- `javascript:`, `data:`, `file:`, `content:`, `intent:` and opaque deep links.
- credential URLs, malformed/out-of-range ports, IPv4/IPv6/IDN/localhost.
- duplicate source IDs in one payload and repeated snapshots.
- same source tab ID from different browsers/devices.
- complete, partial, current-window, protected-pinned, and complete-zero snapshots.
- long titles/notes/tags/groups/device labels, bounded source identifiers, counters, and timestamps.

## B. Database, Paging, and migrations

- fresh install, reset, database open after process death.
- migrations 1→2, 2→3, and 1→3 with data fixtures.
- stable Inbox/default IDs and system-group protection.
- group create/rename/delete, case-insensitive duplicates, dependent tabs/rules.
- 800/801/5,000/25,001-ID operations, SQLite chunking, and transaction interruption.
- Paging refresh/append/retry, empty result, query changes during load, stable scroll key after edits.
- every status/browser/group/device/source-group/tag/pin/notes/stale filter combination.
- all nine sorts and both directions.
- source facet/count consistency against direct SQL.
- complete query-wide selection and selection cleanup after mutations.

## C. Smart views and decks

- save/rename/delete/pin/unpin smart views.
- every query field round-trips through query codec v2.
- apply a view with missing group/device/tag values.
- create a deck from visible, selected, and all-matching records.
- preserve order, de-duplicate membership, cascade deleted tabs.
- transfer a deck with missing/trashed members.
- backup/restore views and decks where source identities merge into existing rows.

## D. Organization and dedupe

- all dedupe modes with tracker stripping on/off.
- newest/oldest/richest/pinned survivor policies.
- notes/tags/pin/time/group/transfer metadata merge.
- preview equals applied plan.
- duplicates remain visible until explicit action.
- RE2 unsupported lookaround/backreference feedback.
- rule priority, ignore-case, continue/stop, disabled rules, and more than 250 ordered rules.
- rule test count versus applied count.
- bulk tags add/remove/replace/clear with empty input and large tag sets.

## E. Lifecycle and maintenance

- Active ↔ Archive/Trash/Snooze.
- restore from every non-active state.
- WorkManager wake at/after due time and process/reboot behavior.
- permanent selected delete and Empty Trash confirmation.
- age-based Trash prune.
- reset while bridge/transfer active.

## F. Android transfer

For every declared package, installed/uninstalled/disabled:

- one tab, zero tabs, 25,001+ tabs, invalid URL, and package removed between detection and launch.
- selected/query/group/deck scopes.
- destination confirmation count, browser identity, and create-new-tab intent extra.
- gentle/balanced/fast pacing.
- cancellation before first/middle/final item.
- rotation/background/foreground/process interruption.
- partial history and per-tab transfer counters.
- clipboard/share behavior for large selections and platform-level failure reporting.

## G. Bridge

- IPv4/IPv6 loopback acceptance and rejection of private-LAN, link-local, ULA, and public clients;
- ADB-forwarded desktop access resolves to the loopback listener.
- valid/invalid/empty/rotated token and timing-independent compare behavior.
- accepted extension origins, arbitrary origin rejection, no-Origin companion request.
- `OPTIONS`, `/health`, v1/v2/v3 import, wrong method/path/content type.
- health preflight before/after expiry and with no tab data.
- malformed request line/header/body/content length/chunking/UTF-8/JSON.
- slow client, socket timeout, request flood, stale limiter pruning.
- session expiry, manual stop, service restart, port collision.
- Android 13 notification denial and Android 15+ `dataSync` timeout callback.

## H. Extensions

- Firefox stable/Beta/Nightly Android API availability.
- normal/private windows according to permission behavior.
- pinned, active, discarded, inaccessible special URLs.
- 0/1/25,001+ sessions without truncation and tab API errors.
- duplicate preview and survivor choice.
- cleanup chunking and confirmation.
- bridge preflight permission denied/timeout/expired/public endpoint/API response.
- remember-token off/default and explicit opt-in.
- Chromium native group titles/colors and missing/deleted group race.

## I. Desktop Link

- no adb, unauthorized/offline/multiple devices.
- no DevTools socket and more than 32 visible sockets.
- port collision and forward cleanup.
- malformed/timeout `/json` targets.
- search, select visible, duplicate selection.
- more than 250 live actions and 25,001+ bridge tabs without count truncation.
- destination same/different source.
- failed destination open must not close source.
- manual close confirmation.
- bridge forward/token rejection/window cleanup.

## J. Backup and export

- backup v1/v2/v3 import and v3 round trip.
- invalid group/rule among valid tabs.
- stable system group and bridge-token exclusion.
- smart views and ordered deck memberships.
- Markdown escaping and grouping.
- CSV quotes, CR/LF normalization, Unicode, formula prefixes, external spreadsheet import.
- bookmarks HTML escaping, folder grouping, browser import in representative browsers.
- readable export excludes Trash; full backup retains the complete supported Trash collection.

## K. UI, accessibility, and performance

- TalkBack labels/traversal and switch/button states.
- font scale 100/130/200%.
- keyboard/D-pad navigation and focus order.
- contrast in light/dark/dynamic/fallback accents.
- long localized browser/group/device names and RTL.
- split-screen, rotation, fold/unfold, tablet drawer/rail.
- reduced motion and haptic settings.
- Paging latency at 1k/10k/25k records.
- dashboard aggregate invalidation cost.
- baseline/startup profile collection and release-mode Macrobenchmark.
- both widgets and all shortcuts after update/reset/process death.

## v1.2 automation and widget regression matrix

- `MaintenancePolicyTest`: positive retention clamping, deterministic cutoffs, and underflow safety.
- `SnapshotJsonCodecTest.maintenancePreferencesRoundTrip`: backup v3 preserves maintenance enablement and retention settings.
- Static contracts require the unique maintenance worker, all four widget receivers/providers, deep-link deck action, and core-policy coverage.
- Android CI must compile WorkManager scheduling, run unit tests and lint, and assemble both debug and community release APKs.
- Device validation should pin all four widgets, launch a deck from the Deck Launcher, toggle automatic maintenance, run maintenance manually, and confirm process-restart persistence.
