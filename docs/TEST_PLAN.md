# TabDeck v1 release test plan

## A. Import and parsing

- Empty, whitespace-only, malformed, future-version, and unrelated JSON files.
- 16 MiB exact boundary and one-byte-over boundary.
- UTF-8 truncation around ASCII, 2-byte, 3-byte, emoji/surrogate pairs, and malformed surrogate input.
- 128 shared-item boundary and duplicate ClipData/stream URIs.
- 25,000 URL boundary and 25,001+ truncation accounting.
- Markdown, nested/balanced parentheses, punctuation, HTML entities, and `www.` URLs.
- `javascript:`, `data:`, `file:`, `content:`, `intent:` and opaque deep links.
- credential URLs, malformed/out-of-range ports, IPv4/IPv6/IDN/localhost.
- duplicate source IDs in one payload and repeated snapshots.
- same source tab ID from different browsers/devices.
- complete, partial, current-window, protected-pinned, and complete-zero snapshots.
- oversized titles/notes/tags/groups/device IDs/counters/timestamps.

## B. Database, Paging, and migrations

- fresh install, reset, database open after process death.
- migrations 1→2, 2→3, and 1→3 with data fixtures.
- stable Inbox/default IDs and system-group protection.
- group create/rename/delete, case-insensitive duplicates, dependent tabs/rules.
- 800/801/5,000/25,000-ID operations and transaction interruption.
- Paging refresh/append/retry, empty result, query changes during load, stable scroll key after edits.
- every status/browser/group/device/source-group/tag/pin/notes/stale filter combination.
- all nine sorts and both directions.
- source facet/count consistency against direct SQL.
- query-wide selection cap and selection cleanup after mutations.

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
- rule priority, ignore-case, continue/stop, disabled rules, 250-rule ceiling.
- rule test count versus applied count.
- bulk tags add/remove/replace/clear with empty input and tag limits.

## E. Lifecycle and maintenance

- Active ↔ Archive/Trash/Snooze.
- restore from every non-active state.
- WorkManager wake at/after due time and process/reboot behavior.
- permanent selected delete and Empty Trash confirmation.
- age-based Trash prune.
- reset while bridge/transfer active.

## F. Android transfer

For every declared package, installed/uninstalled/disabled:

- one tab, zero tabs, queue cap, invalid URL, package removed between detection and launch.
- selected/query/group/deck scopes.
- destination confirmation count and browser identity.
- gentle/balanced/fast pacing.
- cancellation before first/middle/final item.
- rotation/background/foreground/process interruption.
- partial history and per-tab transfer counters.
- clipboard/share Binder-size protection.

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
- 0/1/large sessions, 25,000 cap, tab API errors.
- duplicate preview and survivor choice.
- cleanup chunking and confirmation.
- bridge preflight permission denied/timeout/expired/public endpoint/API response.
- remember-token off/default and explicit opt-in.
- Chromium native group titles/colors and missing/deleted group race.

## I. Desktop Link

- no adb, unauthorized/offline/multiple devices.
- no DevTools socket and >32 sockets.
- port collision and forward cleanup.
- malformed/timeout `/json` targets.
- search, select visible, duplicate selection.
- 250 live-action and 25,000 bridge caps.
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
- readable export excludes Trash; full backup retains bounded Trash.

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
