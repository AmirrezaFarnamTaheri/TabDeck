# TabDeck v1 Deep Improvement and Expansion Plan

## Purpose

This document is the implementation contract for the next TabDeck cycle. It converts the deep forensic audit into an ordered delivery program with explicit architecture, product, reliability, security, testing, release, and documentation gates.

TabDeck remains one product with three execution surfaces:

1. **Android app** — canonical library, saved views, decks, tags, import/export, transfer orchestration, widgets, diagnostics, and user-facing state.
2. **Browser connectors** — browser-specific capture, restore, grouping, and diagnostics within official extension APIs.
3. **Windows companion** — USB/ADB and DevTools-assisted workflows that Android cannot legitimately perform itself.

The goal is not a rewrite. The goal is disciplined convergence around the strongest existing implementation, removal of drift, and measurable improvement in user power, reliability, accessibility, operability, and release quality.

## Current verified baseline

The current `main` branch has already reached a strong build baseline:

- committed Gradle wrapper with integrity verification
- synchronized public versions
- executable Kotlin core checks
- static project validation
- Kotlin/Java compilation
- Android unit tests
- Android lint
- debug APK assembly
- CI artifact upload
- corrected PowerShell parser failure
- corrected API-27 resource split
- coherent Gradle/AGP/Kotlin/KSP/Compose toolchain
- Room paging dependency and build fixes
- release workflow that fails closed when signing material is absent

The remaining release prerequisite is owner-provided persistent Android signing material. Disposable CI-generated release keys are explicitly rejected because they would break future update compatibility.

## Product thesis

TabDeck should become a **browser session control plane** rather than a passive bookmark collector.

The canonical Android library should model captured tabs, browser sessions, user-defined decks, source provenance, saved queries, transfer history, and recovery state. Connectors should expose only the capabilities officially available on their platform. The Windows companion should exist to unlock USB-assisted capture, cross-browser migration, and live recovery for browsers that expose supported DevTools targets.

## Architectural invariants

The following invariants are mandatory:

- Android owns canonical user-visible state.
- External browser tab IDs are session-scoped identifiers, never durable primary keys.
- Every imported tab receives a stable TabDeck UUID.
- Source identity includes connector, device, browser channel/profile, session/window identity, external tab ID, and first-seen timestamp.
- Bulk mutations are bounded, chunked, cancellable where possible, and produce explicit partial-failure outcomes.
- Destructive actions are either reversible, previewable, or explicitly irreversible.
- No universal browser access is claimed where Android sandboxing prevents it.
- Package visibility uses explicit `<queries>` entries rather than broad package discovery.
- Bridge sessions remain short-lived, authenticated, rate-limited, and restricted to loopback/private-network clients.
- Release signing uses persistent owner-controlled keys.
- CI validates the committed wrapper; it never regenerates wrapper files.
- Documentation describes implemented behavior only.

## Delivery streams

### 1. Adaptive Android control plane

Implement a truly adaptive Compose shell:

- list-detail layout on medium and large widths
- persistent inspector/supporting pane where appropriate
- bottom navigation on compact widths
- navigation rail or drawer on larger widths
- foldable and desktop-window support driven by window size classes
- state restoration across window resizing and process recreation
- keyboard and mouse affordances for ChromeOS and desktop-style Android environments

Power-user interactions:

- command palette with searchable actions
- keyboard shortcuts for selection, filtering, pinning, moving, archiving, restoring, and launching
- right-click/context menus on pointer-capable devices
- split comparison view for duplicate clusters and session diffs
- query-wide selection with visible scope and safety caps
- bulk tag add/remove/replace/clear
- bulk pin/unpin, regroup, archive, snooze, restore, trash, and transfer
- persistent filter chips and saved smart views
- ordered launch decks with drag-reorder support

Acceptance criteria:

- core flows operate on compact, medium, and expanded widths
- no action depends on an item being present in the currently loaded paging window
- selection state remains consistent after database refreshes
- all destructive bulk operations show scope, preview, and outcome
- keyboard-only navigation covers all primary workflows

### 2. Accessibility and interaction quality

Treat accessibility as a release gate:

- semantic roles and state descriptions for rows, groups, decks, filters, and widgets
- custom accessibility actions for frequent row operations
- traversal groups and explicit traversal ordering where default order is insufficient
- minimum 48 dp targets
- large-text support without clipped controls
- contrast validation across light/dark/dynamic color themes
- TalkBack, Switch Access, keyboard-only, and mouse testing
- Compose automated accessibility checks in instrumentation tests

Acceptance criteria:

- no blocking issues in TalkBack and Switch Access smoke tests
- all actionable controls have accessible names and roles
- large-font layouts remain usable without horizontal clipping
- focus and traversal order match visual hierarchy

### 3. Large-library performance

Preserve Room + Paging as the canonical large-library backbone and add measurable budgets:

- database-side filtering, sorting, grouping, and facets
- stable keys and `contentType` hints in lazy layouts
- bounded query projections
- no whole-library Compose snapshots
- chunked SQLite operations
- baseline profiles
- macrobenchmarks for startup, scroll, search, and import
- recomposition tracing for high-frequency screens
- background parsing and normalization on bounded dispatchers

Initial performance budgets:

- cold start benchmark recorded and tracked
- library scroll has no sustained jank in a 25,000-item synthetic dataset
- common search/filter query returns first page without blocking the main thread
- 25,000-item import remains bounded in memory and reports progress
- widget refresh does not scan the whole library

### 4. Import, export, backup, and recovery

Expand the portability model while preserving strict validation:

- import provenance per source connector/device/browser/session
- complete JSON backup with versioned schema
- Markdown, CSV, bookmarks HTML, and plain-text exports
- export formula neutralization and HTML escaping
- round-trip fixtures for every format
- bounded file and URI ingestion
- partial import reporting
- zero-tab complete snapshots that can reconcile stale source records
- explicit merge/replace/append restore modes
- migration rehearsal for all persisted schema versions
- recovery bundle containing sanitized diagnostics and non-secret metadata

Acceptance criteria:

- every supported export has a round-trip or golden-fixture test
- future backup versions are rejected safely
- invalid records do not corrupt valid records in the same import
- bridge tokens and signing material never appear in backups or diagnostics

### 5. Transfer and background-work redesign

Use the correct Android execution primitive for each workload:

- unique WorkManager jobs for ordinary imports, reconciliation, and deferred maintenance
- user-initiated transfer jobs where platform support and UX justify them
- foreground service only for explicitly user-visible operations that require it
- cancellation and progress propagation
- idempotency keys for connector imports
- retry policies limited to transient failures
- bounded backoff and retry count
- durable transfer history with partial-success details
- Android 15 timeout handling retained

Acceptance criteria:

- duplicate work requests converge to one canonical job
- retries cannot duplicate imported tabs
- cancellation leaves database state consistent
- partial transfers record opened, failed, skipped, and unresolved items separately

### 6. Widget suite

Deliver a coherent Glance widget family:

- **Library Health** — active, archived, trash, duplicates, stale sources
- **Quick Capture** — share/import shortcuts and bridge status
- **Deck Launcher** — configurable named deck with browser target
- **Transfer Status** — active/recent transfer progress and recovery action

Required quality:

- optional configuration and reconfiguration
- in-app pin requests where supported
- generated previews for supported Android versions
- no whole-library work during widget updates
- deterministic empty/error states

### 7. Chromium connector modernization

Evolve the Chromium connector into a richer MV3 side-panel experience:

- current-window and all-window scopes
- `chrome.tabs`, `chrome.tabGroups`, and `chrome.sessions`
- native group title/color preservation
- session recovery and recently closed support where available
- explicit diagnostic mode
- bridge capability negotiation
- token storage in session-scoped storage by default
- optional bookmark/reading-list export hooks
- minimal permissions and clear permission rationale

The connector must not treat browser tab IDs as durable across restarts.

### 8. Firefox Android connector hardening

Maintain a distinct Android Firefox connector path:

- Manifest V2 while Android support requires it
- `browser_specific_settings.gecko_android`
- Firefox, Beta, and Nightly channel labels
- explicit permission onboarding
- current/all-window capture
- pinned-tab protection
- duplicate preview and cleanup
- install-from-file documentation for development distribution
- AMO packaging path for signed distribution

### 9. Windows companion v2

Graduate the PowerShell utility into a first-class desktop companion while retaining the script as a fallback/reference implementation.

Preferred target:

- .NET desktop application with WinUI 3 or equivalent native Windows shell
- portable self-contained single-file package
- MSIX packaging path
- device discovery and authorization diagnostics
- ADB forward lifecycle management
- supported DevTools target enumeration
- safe open-before-close transfer semantics
- browser/session comparison
- recovery bundle export
- structured logs with correlation IDs
- no browser profile database scraping

Acceptance criteria:

- temporary ADB forwards are removed on normal exit and crash recovery
- closing source targets occurs only after destination verification
- unsupported browsers are reported explicitly
- portable package runs without a machine-wide .NET installation

### 10. Security and privacy hardening

Required controls:

- explicit Android package queries
- no `QUERY_ALL_PACKAGES`
- localhost/private-network bridge restrictions
- strict Origin and content-type validation
- short-lived random bridge tokens
- constant-time token comparison
- request/body/header/tab-count bounds
- rate limiting and stale-client pruning
- no secret-bearing logs
- protected `release` environment for signing material
- least-privilege `GITHUB_TOKEN` permissions
- dependency and wrapper integrity checks
- extension permission minimization
- sanitized diagnostic exports

### 11. Observability and diagnostics

Add actionable, privacy-preserving diagnostics:

- correlation ID for bridge imports and transfers
- structured event model for import, normalization, dedupe, persistence, open, close, and reconciliation stages
- local diagnostic timeline
- bounded rotating logs
- sanitized diagnostic bundle export
- health screen covering database, bridge, WorkManager, widgets, browser detection, and connector compatibility
- explicit failure taxonomy and remediation hints

Do not add remote telemetry until there is a defined privacy policy, user consent model, data-retention policy, and operational consumer.

### 12. CI/CD and release convergence

CI must remain deterministic and non-mutating:

- checkout
- JDK setup
- committed-wrapper verification
- project validator
- executable core checks
- unit tests
- Android lint
- debug APK assembly
- PowerShell parser validation
- connector JS/manifest checks
- artifacts and diagnostics upload

Release must:

- use a protected `release` environment
- build from a real commit
- validate requested semantic version
- materialize persistent owner-provided signing material
- build signed APK and AAB
- verify APK with `apksigner`
- verify AAB with `jarsigner -verify -strict`
- package connectors, desktop artifacts, source, checksums, and manifest
- produce attestations where repository/plan support allows
- create or verify the tag only after all gates pass
- publish a GitHub Release

## Testing matrix

### Unit

- URL extraction and canonicalization
- IDN/IPv6/default-port behavior
- tracker stripping modes
- duplicate clustering and survivor selection
- rule compilation and application
- saved-view serialization
- deck ordering and membership
- import parser edge cases
- export escaping
- stable source identity

### Integration

- Room migrations
- Paging queries and facets
- bulk actions
- import/export round trips
- snapshot reconciliation
- transfer-history persistence
- WorkManager uniqueness and cancellation
- widget aggregate queries

### Instrumentation

- adaptive navigation
- list-detail state restoration
- command palette
- keyboard and pointer flows
- accessibility semantics
- widget configuration
- process recreation

### Manual platform validation

- Android phone, tablet, foldable, and resizable desktop window
- Firefox Android stable/beta/nightly
- Chromium desktop connector variants
- ADB-authorized Android Chromium targets
- USB disconnect/reconnect
- bridge token expiry and rotation
- partial transfer failure and recovery

## Prioritized implementation sequence

### Phase 0 — Preserve the release baseline

- keep `main` green
- configure protected release environment
- install persistent signing secrets
- produce signed v1.0.0 APK/AAB
- verify signatures, checksums, and release assets

### Phase 1 — Reliability and data integrity

- stable identity model
- import/export round-trip suite
- WorkManager orchestration
- partial-failure and recovery semantics
- diagnostic correlation IDs

### Phase 2 — Adaptive UX and accessibility

- list-detail layout
- rail/drawer adaptation
- keyboard/mouse support
- command palette
- semantics and assistive-technology validation

### Phase 3 — Widgets and connector modernization

- widget suite
- Chromium side panel
- Firefox Android hardening
- bridge capability negotiation

### Phase 4 — Windows companion v2

- native desktop project
- portable package
- MSIX path
- structured diagnostics
- hardened ADB/DevTools lifecycle

### Phase 5 — Distribution and performance

- baseline profiles and macrobenchmarks
- Play internal testing automation
- extension-store packaging
- Windows signing and installer automation

## PR completion checklist

A feature or remediation is complete only when all applicable items are checked:

- [ ] production code implemented
- [ ] persistence/schema implications handled
- [ ] configuration synchronized
- [ ] tests cover success and failure paths
- [ ] accessibility impact reviewed
- [ ] performance impact measured or bounded
- [ ] security/trust boundary reviewed
- [ ] diagnostics and recovery behavior present
- [ ] documentation matches implementation
- [ ] clean CI passes
- [ ] release/rollback implications documented
- [ ] no temporary workflows, debug residue, or secrets remain

## Readiness verdict

The current repository is **conditionally ready** for a signed v1.0.0 release. Source correctness, CI, lint, unit tests, static validation, and debug assembly are verified. Production publication remains blocked only by repository-owner signing secrets and protected-environment configuration.

The broader improvement program should proceed incrementally from this green baseline. A broad rewrite is rejected. The highest-value path is release stabilization, reliability/data integrity, adaptive UX/accessibility, connector modernization, and only then a first-class Windows companion.
