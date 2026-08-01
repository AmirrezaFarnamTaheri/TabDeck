# TabDeck v1 final implementation record

## Product shape

TabDeck ships as one local-first product with three execution surfaces:

1. **Android app** — canonical inventory, search, groups, tags, notes, saved views, decks, imports, exports, transfer orchestration, widgets, and diagnostics.
2. **Browser connectors** — Firefox Android and Chromium desktop capture through official extension APIs.
3. **Windows Desktop Link** — explicit user-authorized ADB and DevTools workflows for supported Android Chromium builds.

No cloud backend, account system, telemetry service, application store, or hosted synchronization service is required.

## Completed reliability and data-integrity work

- TabDeck UUID remains the durable primary key.
- External browser tab IDs are session-scoped and transformed into bounded opaque identities.
- `identityVersion == 1` is required before complete-snapshot reconciliation.
- Legacy unscoped identities remain importable but cannot authorize destructive missing-tab actions.
- Typed backup decoding separates valid backups, unrelated input, and rejected backup-shaped input.
- Rejected backups never fall through to ordinary URL extraction.
- Duplicate source identities inside one payload are coalesced deterministically.

## Completed bridge trust work

- The HTTP bridge binds only to loopback.
- The canonical endpoint is `http://127.0.0.1:48721/api/v3/import`.
- Desktop access uses explicit `adb forward tcp:48721 tcp:48721`.
- Direct LAN exposure is disabled.
- Sessions remain token-authenticated, rate-limited, bounded, foreground-visible, revocable, and time-limited.
- v1/v2 import routes remain compatibility aliases through the current validation path.

## Completed Windows companion work

- Recognized Chromium-family DevTools sockets are allowlisted.
- Unsupported sockets are reported and skipped.
- Temporary forwards are removed on refresh and normal exit, with stale-forward recovery on the next launch.
- Destination creation is verified before optional source closure.
- Failed destination verification leaves the source tab open.
- Long transfers pump UI progress by processed attempts, including failures.
- The portable PowerShell fallback does not require a separately installed .NET application runtime.

## Completed release and identity work

- The original stacked-tab TabDeck mark is used by Android launcher icons, Android themed icons, Firefox, Chromium, Desktop Link, and release branding archives.
- Browser extension manifest raster icons use the same mark at every declared size.
- GitHub Releases require no protected environment or repository secrets.
- A repository-published community key provides stable Android upgrade signatures.
- The key is intentionally public and is not presented as exclusive publisher authentication.
- Each release builds one minified APK; no AAB or application-store path is produced.
- Release assets include the APK, connectors, Desktop Link, branding, deterministic source, validation report, manifest, checksums, and optional R8 mapping.
- The workflow verifies the source/tag binding, public key fingerprint, APK alignment and signature, connector DOM contracts, archive integrity, and checksums.
- Merging a verified version/release change into `main` automatically creates or verifies the version tag and publishes the GitHub Release.

Community certificate SHA-256:

`8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`

## Verification gates

Pull-request CI runs:

- synchronized version checks;
- committed Gradle-wrapper validation;
- executable Kotlin core checks;
- static cross-component validation;
- secretless release and branding validation;
- browser connector DOM-binding validation;
- performance-budget configuration validation;
- PowerShell parsing and Desktop Link contract tests;
- Android unit tests;
- debug and minified release lint/build;
- community APK alignment and signature verification;
- deterministic APK, source, connector, Desktop Link, and branding packaging;
- checksum verification and artifact/report upload.

The automatic `main` release additionally runs:

- version-tag derivation and source binding;
- community-key materialization and fingerprint verification;
- release-mode unit tests and lint;
- minified signed APK assembly;
- APK alignment and signature verification;
- release-manifest provenance checks;
- SHA-256 verification;
- version-tag creation or verification;
- GitHub Release publication.

## Architectural invariants

- Android owns canonical user-visible state.
- User-owned notes, tags, groups, status, and transfer history survive connector refreshes.
- Destructive behavior is previewable, reversible, or explicitly irreversible.
- Bulk work is bounded and reports partial outcomes.
- Package visibility is explicit; `QUERY_ALL_PACKAGES` is prohibited.
- Browser profile databases, hidden APIs, accessibility scraping, VPN interception, and root are prohibited.
- The bridge remains loopback-only.
- Documentation describes implemented behavior rather than future promises.

## Completion checklist

- [x] durable session-scoped source identity
- [x] typed backup classification and rejection
- [x] loopback-only bridge and ADB forwarding
- [x] shared connector runtime
- [x] connector DOM-binding regression gate
- [x] Windows destination-before-close and forward recovery
- [x] fixed performance budgets
- [x] Android, connector, Desktop Link, and branding identity
- [x] secretless stable community APK signing
- [x] APK-only automatic GitHub Release workflow
- [x] deterministic release archives, manifest, and checksums
- [x] CI release-mode APK packaging and signature validation

The project is complete when the final branch head passes CI and is merged. That merge triggers the Release workflow, which creates or verifies the release tag declared by `version.properties` at the merged commit and publishes the final GitHub release automatically.

## v1.2 implementation checkpoint — automation and recovery

Implemented after the v1.1 guided-utility baseline:

- unique periodic WorkManager maintenance with battery/storage constraints and bounded retry;
- persisted local maintenance result and failure state;
- configurable Trash retention and interactive maintenance controls;
- Deck Launcher and Transfer Status Glance widgets;
- deck-specific widget deep linking into the Open workflow;
- backup-v3 round-trip coverage for the new settings;
- core/static contracts that prevent worker, widget, and deep-link wiring regressions.

This checkpoint intentionally keeps Room schema v3 and backup format v3 because the new state lives in DataStore and the settings decoder supplies backward-compatible defaults.
