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
- GitHub Releases require no protected environment or repository secrets.
- A repository-published community key provides stable Android upgrade signatures.
- The key is intentionally public and is not presented as exclusive publisher authentication.
- Each tag release builds one minified APK; no AAB or application-store path is produced.
- Release assets include the APK, connectors, Desktop Link, branding, deterministic source, validation report, manifest, checksums, and optional R8 mapping.
- The workflow verifies the source/tag binding, public key fingerprint, APK alignment and signature, archive integrity, and checksums.

Community certificate SHA-256:

`8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`

## Verification gates

Pull-request CI runs:

- synchronized version checks;
- committed Gradle-wrapper validation;
- executable Kotlin core checks;
- static cross-component validation;
- secretless release and branding validation;
- performance-budget configuration validation;
- PowerShell parsing and Desktop Link contract tests;
- Android unit tests;
- Android lint;
- debug APK assembly;
- deterministic source, connector, Desktop Link, and branding packaging;
- artifact/report upload.

Tag release additionally runs:

- community-key materialization and fingerprint verification;
- release-mode unit tests and lint;
- minified signed APK assembly;
- APK alignment and signature verification;
- release-manifest provenance checks;
- SHA-256 verification;
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
- [x] Windows destination-before-close and forward recovery
- [x] fixed performance budgets
- [x] Android, connector, Desktop Link, and branding identity
- [x] secretless stable community APK signing
- [x] APK-only GitHub Release workflow
- [x] deterministic release archives, manifest, and checksums
- [x] CI packaging and release-contract validation

The project is complete when the final branch head passes CI and is merged. Publishing v1.0.0 then requires only creating or dispatching the matching GitHub tag.
