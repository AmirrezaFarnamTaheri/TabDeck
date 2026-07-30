# TabDeck v1 architecture

## 1. System objective

The Android application is the product. Browser extensions and Desktop Link are optional acquisition adapters that increase user control over Android browser sessions; they are not substitute desktop products.

The architecture separates six operations:

1. **Acquire** a browser-provided or user-shared snapshot.
2. **Validate** untrusted URLs and metadata at a single persistence boundary.
3. **Store** an independent local inventory.
4. **Understand** the inventory through aggregates, facets, saved views, and duplicate analysis.
5. **Organize** through groups, tags, rules, status lanes, and reusable decks.
6. **Transfer** selected URLs to an explicit Android browser with bounded, observable progress.

## 2. Capability boundary

Android applications run in a limited-access sandbox. Package visibility can reveal only declared/automatically visible packages and does not grant access to another browser's private tab store. TabDeck therefore does not use or claim a universal cross-browser tab-read API.

Official references:

- Android privacy/app sandbox: <https://developer.android.com/privacy>
- Package visibility: <https://developer.android.com/training/package-visibility>
- Declaring the smallest needed package set: <https://developer.android.com/training/package-visibility/declaring>
- Opening URLs with limited package visibility: <https://developer.android.com/training/package-visibility/use-cases>

## 3. Acquisition adapters

- Android `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
- `tabdeck://import` deep links (`url` and `text` parameters)
- clipboard/paste dialog with live extraction preview
- Storage Access Framework text, HTML, JSON, Markdown, or URL-list import
- Firefox Android WebExtension snapshots
- Chromium desktop extension snapshots with native desktop group metadata
- Windows Desktop Link snapshots from user-authorized ADB/DevTools targets

The intent adapter handles text, ClipData, deep-link fields, one/many streams, URI de-duplication, MIME checks, a 128-item ceiling, a 16 MiB aggregate text ceiling, and a 25,000-URL ceiling. UTF-8 truncation never splits a surrogate pair or emits malformed text.

All adapters produce untrusted candidates. Only the repository writes persistent state.

## 4. Persistence and query model

Room schema v3 owns queryable/high-volume state:

- `tabs`
- `regex_rules`
- `groups`
- `smart_views`
- `decks`
- `deck_tabs`
- `transfer_history`
- `import_history`

Preferences DataStore owns small settings and bridge secrets/counters.

### Library path

The main library is a Room-backed `Pager`/`PagingSource`, not an in-memory projection of the full database. Search, filters, sort direction, and sort mode are compiled into bounded SQL. Compose consumes `LazyPagingItems` using stable item IDs and reusable content types; placeholders are disabled.

Dashboard totals, group/browser/source facets, recent history, smart-view summaries, and deck summaries are separate small flows. This avoids invalidating or copying the complete inventory whenever a counter changes.

Official references:

- Compose lazy lists/grids and stable keys: <https://developer.android.com/develop/ui/compose/lists>
- Paging with Compose: <https://developer.android.com/develop/ui/compose/quick-guides/content/lazily-load-list>
- `LazyPagingItems` keys/content types: <https://developer.android.com/reference/kotlin/androidx/paging/compose/LazyPagingItems>

### Write invariants

- Source identity is `(sourceDevice, browser, sourceTabId)`.
- Repeated source snapshots update connector-owned metadata while preserving user-owned notes, tags, TabDeck group, status, and transfer counters.
- A complete snapshot with zero tabs is meaningful and can reconcile previously stored source tabs according to policy.
- System Inbox uses a stable protected ID.
- Group rename/delete updates dependent tab assignments and rule destinations transactionally.
- Case-insensitive group uniqueness is enforced.
- Large ID operations are chunked below SQLite parameter ceilings.
- Externally supplied and interactively edited metadata is bounded and sanitized again at the repository boundary.

## 5. Processing engines

### URL pipeline

`UrlExtractor` handles prose, Markdown links, HTML entities, `www.` promotion, terminal punctuation, and balanced parentheses.

`UrlNormalizer`:

- accepts only HTTP/HTTPS;
- rejects credentials, control characters, malformed authorities, invalid/out-of-range ports;
- canonicalizes IDN hosts, default ports, repeated slashes, fragments, query order, and tracker removal;
- supplies exact, normalized, host, and host/path keys.

### Deduplication

`DedupeEngine` builds deterministic clusters and survivor plans for exact URL, normalized URL, or host/path matching. Policies include newest, oldest, richest metadata, and pinned-first. Optional merge combines tags, notes, earliest creation, latest observation, pin state, group value, and transfer metadata. Duplicate removal moves copies to Trash.

### Categorization

`RegexCategorizer` uses RE2/J rather than Java backtracking regex. Enabled rules are priority-ordered, compiled once, and can target URL, title, host, source group, tags, or combined text. A rule can set a TabDeck group, enrich tags, and continue or stop. Rule testing reports active-library match counts before mutation.

## 6. Control model

Transient selection is separate from Room. The user can select loaded items or resolve every matching query up to the 25,000-record control ceiling. Query-wide operations include pin/unpin, tag add/remove/replace/clear, group assignment, archive, snooze, Trash, deck creation, copy/share, and transfer.

Smart views serialize the full library query. Decks preserve explicit ordered membership and survive filter changes. Backup v3 preserves both.

## 7. Transfer model

`BrowserTransferCoordinator`:

- validates a declared explicit destination package;
- checks installation readiness;
- accepts only HTTP/HTTPS destinations;
- bounds the queue;
- launches at gentle/balanced/fast pacing;
- reports current/opened/failed totals;
- supports cancellation;
- writes partial outcomes in `NonCancellable` cleanup.

A transfer is a copy/open request. TabDeck does not claim universal source-tab closure or native destination-group creation.

## 8. Bridge lifecycle

The local bridge is a user-started foreground service. It:

- binds to loopback or all interfaces according to explicit scope;
- expires after 5–120 minutes;
- exposes `/health` and `/api/v1|v2|v3/import`;
- uses a rotating 64-hex token and constant-time comparison;
- validates origin, method, path, content type, headers, request ID, body, UTF-8, source address, rate, and tab count;
- allows only loopback/private/link-local clients for LAN scope;
- stops on expiry, explicit stop, Android service timeout, or process teardown.

Android 15+ gives `dataSync` foreground services a shared six-hour allowance per 24 hours while the app is backgrounded and invokes `Service.onTimeout`; TabDeck sessions are intentionally much shorter and stop themselves. Official reference: <https://developer.android.com/develop/background-work/services/fgs/timeout>

## 9. Connector boundaries

### Firefox Android

The Manifest V2 development connector uses Firefox's WebExtension tabs API, local storage, optional HTTP host permission, a user-visible preview, duplicate selection, protected pinned tabs, bridge preflight, and explicit send/cleanup actions. API availability must be tested against each Firefox Android channel.

Official reference: <https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/>

### Chromium desktop

The Manifest V3 companion uses `tabs`, `tabGroups`, storage, optional HTTP host permission, a preview, bridge preflight, and explicit send/cleanup actions. It is not an Android extension.

### Windows Desktop Link

Desktop Link uses an already installed `adb.exe`, an already authorized device, visible `*devtools_remote*` sockets, temporary local forwards, and `/json`, `/json/new`, and `/json/close`. It never enables developer options, authorizes the host, or reads browser profile files.

Official Chrome reference: <https://developer.chrome.com/devtools/docs/remote-debugging>

## 10. Portability

- **Full backup v3**: tabs, rules, groups, smart views, ordered deck membership, non-secret settings, bounded import/transfer history. Live token/session state is excluded.
- **Markdown**: grouped readable outline.
- **CSV**: rich metadata table with RFC-style quoting and formula-prefix neutralization.
- **Netscape bookmarks HTML**: TabDeck groups become browser-importable folders; markup and attributes are escaped.

Readable exports exclude Trash. Future backup versions and unrelated JSON are rejected instead of silently becoming empty data.
