# TabDeck v1 Deep Improvement and Expansion Program

## Purpose

This document is the implementation contract for TabDeck’s next delivery cycle. It records platform boundaries, immutable evidence, executable acceptance gates, and the ordered path from the current Android/browser/Windows product to a stronger browser-session control plane.

TabDeck remains one product with three execution surfaces:

1. **Android app** — canonical library, saved views, decks, tags, imports, exports, transfer orchestration, widgets, diagnostics, and user-visible state.
2. **Browser connectors** — browser-specific capture and restore within official extension APIs.
3. **Windows companion** — user-authorized USB/ADB and DevTools workflows that Android cannot perform directly.

A broad rewrite is rejected. Changes must preserve the green baseline and land as independently testable slices.

## Immutable verified baseline

The implementation baseline is commit `b486aa826b82512b45e84e31cbb35b81fc9fac78` (“Converge Android toolchain and repair Kotlin build”). Its retained CI artifact is `TabDeck-CI-b486aa826b82512b45e84e31cbb35b81fc9fac78.zip` with SHA-256 `98c990fccd3dbbbb881479a9b89628bb1afd97910ecc642de41345b848e3931e`.

That evidence covers:

- committed and checksum-pinned Gradle wrapper;
- AGP 9.3.1, Gradle 9.6.1, Kotlin Compose 2.4.10, and KSP 2.3.10 convergence;
- synchronized public versions;
- executable Kotlin core checks;
- static cross-component validation;
- Kotlin/Java compilation;
- Android unit tests and lint;
- debug APK assembly and artifact upload.

Subsequent workflow-only cleanup ended at commit `cf0344ce03ecc015bf6f63a3437c6f5b8ef9403e`. Every implementation commit in this PR must obtain its own green CI run and artifact; previous evidence is not treated as proof for changed code.

The remaining production-publication gates are explicit and external to source correctness:

1. protected GitHub `release` environment configured;
2. persistent owner-controlled keystore secrets installed;
3. `TABDECK_RELEASE_CERT_SHA256` configured to the approved signing certificate;
4. signed APK and AAB fingerprints both match that approved certificate;
5. tag resolves to the immutable source commit recorded in the release manifest;
6. checksums, attestation, and GitHub Release publication complete successfully.

## Product thesis

TabDeck is a **browser session control plane**, not a passive bookmark collector. Android owns canonical user-visible state. Connectors expose only officially available browser capabilities. The Windows companion handles USB-assisted capture, cross-browser movement, and live recovery through explicit ADB and DevTools authorization.

## Architectural invariants

- Android owns canonical user-visible state.
- Every stored tab has a TabDeck UUID independent from browser identifiers.
- External browser tab IDs are session-scoped and may be reused after restart.
- Durable source equality uses connector, device, browser channel/profile, session/window identity, and external tab ID.
- `firstSeenAt` is metadata only and never participates in identity equality or retry deduplication.
- A bounded opaque source-tab ID contains a one-way session fingerprint; the raw session token is not persisted.
- Legacy unscoped source IDs remain readable but cannot authorize destructive complete-snapshot reconciliation.
- Bulk mutations are bounded, chunked, and report partial outcomes.
- Destructive actions are reversible, previewable, or explicitly irreversible.
- Package visibility uses explicit `<queries>` entries; `QUERY_ALL_PACKAGES` is prohibited.
- The HTTP bridge is loopback-only. Desktop access uses explicit ADB forwarding.
- Direct LAN exposure remains disabled until authenticated TLS, peer/address allowlisting, certificate lifecycle management, explicit opt-in, and revocation are implemented.
- Release signing uses persistent owner-controlled keys and an approved certificate fingerprint.
- CI validates the committed wrapper and never regenerates it.
- Documentation describes implemented behavior only.

The critical decisions are maintained in `docs/adr/`.

## Implemented reliability and data-integrity tranche

### Durable source identity

Implemented contracts:

- `SourceIdentity` produces deterministic, bounded, session-scoped opaque source IDs;
- the same session/tab pair resolves to the same identity on retry;
- the same raw browser tab ID in a later session resolves to a different identity;
- connectors send `sourceSessionId` and `identityVersion`;
- complete snapshots are honored only with provable browser, device, session, and per-tab identity;
- duplicate source identities inside one payload are coalesced deterministically;
- Desktop Link derives a session fingerprint from the authorized device and visible DevTools sockets.

Acceptance gates:

- pure Kotlin core check;
- JVM unit tests for deterministic identity, restart separation, legacy compatibility, sanitization, and bounds;
- static cross-component validator checks parser and connector participation.

### Typed backup classification

`SnapshotJsonCodec.decodeClassified` returns one of:

- `Success(snapshot)` — a supported, valid TabDeck backup;
- `NotBackup` — definitively unrelated text/JSON, eligible for ordinary URL extraction;
- `Rejected(reason)` — malformed, unsupported, or invalid backup-shaped input.

`TabDeckViewModel.importDocument` runs URL extraction only for `NotBackup`. A rejected backup can no longer be partially reinterpreted as a URL list.

Acceptance gates:

- pure classifier checks for plain text, malformed backup-shaped JSON, and unrelated JSON;
- future versions and missing tab inventories are rejected in the codec;
- static validator requires the typed decode path in the ViewModel.

### Bridge trust boundary

Implemented policy:

- bind only to loopback;
- advertise only `http://127.0.0.1:48721/api/v3/import`;
- accept only loopback clients;
- remove private-network CORS claims;
- coerce imported/persisted legacy LAN scope to `THIS_DEVICE`;
- disable LAN scope in the UI with an explicit security explanation;
- restrict Firefox and Chromium connector endpoint validation to loopback;
- support desktop use through user-authorized ADB port forwarding;
- retain token rotation, expiry, constant-time comparison, strict origins, bounds, and rate limits.

Direct LAN access is not considered authenticated merely because an address is private or an Origin header is accepted.

### Release provenance and signing identity

The release workflow now:

1. runs inside the protected `release` environment;
2. records the immutable checked-out source commit;
3. verifies an existing tag resolves to that commit;
4. validates the owner-provided keystore certificate against `TABDECK_RELEASE_CERT_SHA256`;
5. builds signed APK/AAB outputs;
6. verifies APK alignment and signature validity;
7. verifies AAB signature validity;
8. extracts APK and AAB certificate SHA-256 fingerprints independently;
9. requires both fingerprints to equal the approved fingerprint;
10. records source commit, release tag, and signing fingerprint in schema-v2 release manifest provenance;
11. verifies checksums and manifest bindings before upload;
12. creates or verifies the tag against the same commit;
13. attests and publishes only after all gates pass.

## Adaptive Android control plane

The existing adaptive shell, paging-backed library, command palette, list/grid density controls, smart views, decks, query-wide operations, and navigation adaptation remain the foundation.

Further UI work must preserve these acceptance criteria:

- compact, medium, and expanded widths remain functional;
- no bulk action depends on the currently loaded paging window;
- selection survives database invalidation safely;
- keyboard-only navigation covers primary workflows;
- pointer context actions are equivalent to touch actions;
- destructive operations show scope and outcome;
- large text does not clip controls;
- TalkBack traversal follows visual hierarchy.

## Measurable performance budgets

The executable budget configuration is `tools/performance-budgets.json`; `tools/performance_budget.py` validates configuration and benchmark results.

| Metric | Pass threshold | Reference device | Dataset | Measurement |
|---|---:|---|---|---|
| Cold start p95 | ≤ 1,200 ms | Pixel 6/API 36 or equivalent | 25,000 tabs | Macrobenchmark `StartupTimingMetric`, 10 cold iterations |
| Scroll jank | ≤ 3.0% | Pixel 6/API 36 or equivalent | 25,000 tabs, dense list | `FrameTimingMetric`, 10 scripted journeys |
| Search first-page p95 | ≤ 250 ms | Pixel 6/API 36 or equivalent | 25,000 mixed-facet tabs | query commit to first Paging item, 30 runs |
| Import peak RSS | ≤ 256 MiB | Pixel 6/API 36 or equivalent | 25,000-tab JSON snapshot | Perfetto process RSS peak |
| Import throughput | ≥ 1,000 tabs/s | Pixel 6/API 36 or equivalent | 25,000-tab JSON snapshot | accepted tabs / parse+persist duration, median of 5 |
| Widget refresh p95 | ≤ 250 ms | Pixel 6/API 36 or equivalent | 25,000 tabs | update request to state publication, 30 runs |

CI rule: fail if a metric breaches its absolute budget or regresses more than 10% from the approved baseline, whichever is stricter. Device-produced results are passed to `performance_budget.py --results <file>`.

## Import, export, backup, and recovery

Required guarantees:

- bounded text, file, URI, JSON, and bridge ingestion;
- source provenance per connector/device/browser/session;
- typed rejection of malformed/unsupported backups;
- invalid individual tab records do not corrupt accepted records;
- zero-tab complete snapshots can reconcile stale source records only with provable session identity;
- readable exports preserve escaping and spreadsheet-formula hardening;
- bridge tokens and signing material never appear in backups or diagnostics;
- recovery diagnostics contain sanitized metadata only.

## Transfer and background work

Ordinary deferred maintenance should use unique WorkManager jobs. User-visible browser opening remains an explicit foreground interaction. Every transfer must have cancellation, progress, bounded retries for transient errors, and durable partial-success details.

Acceptance criteria:

- duplicate job requests converge;
- retries cannot duplicate imported tabs;
- cancellation leaves a transactionally consistent database;
- partial transfer results separate opened, failed, skipped, and unresolved tabs;
- Android 15 foreground-service timeout handling remains active.

## Widget suite

The Library Health and Quick Capture widgets remain query-bounded and deterministic. Future Deck Launcher and Transfer Status surfaces must use aggregate queries rather than whole-library scans and must support empty/error/configuration states.

## Browser connectors

### Chromium desktop

- Manifest V3;
- session-scoped token storage when supported;
- session-scoped source identity;
- native group metadata preservation;
- current/all-window scopes;
- duplicate preview/cleanup separated from import;
- loopback-only bridge endpoint, including ADB-forwarded loopback;
- capability/health preflight before sending inventory.

### Firefox Android

- distinct Firefox Android connector path;
- Manifest V2 while Android support requires it;
- session-scoped identity when session storage is available;
- complete snapshots disabled when session persistence cannot be proven;
- pinned-tab protection;
- duplicate preview and bounded cleanup;
- loopback-only bridge endpoint;
- stable/Beta/Nightly labeling and install documentation.

## Windows companion safety and portability

The current PowerShell companion remains the verified fallback while a packaged native UI is evaluated.

Implemented safety contracts:

- recognized Chromium-family DevTools sockets are allowlisted;
- unsupported/unrecognized sockets are reported explicitly;
- every temporary forward is removed on refresh and normal window exit;
- destination creation is followed by polling-based destination verification;
- source closure occurs only after destination verification;
- a stable source-session fingerprint is sent with bridge payloads;
- bridge token is never stored;
- the portable script has no machine-wide .NET application-runtime dependency.

`desktop-link/Test-TabDeckLink.ps1` parses the script and verifies those contracts in CI.

Manual/integration matrix:

1. normal exit removes DevTools and bridge forwards;
2. forced interruption followed by next startup detects/replaces stale forwards;
3. destination verification failure leaves the source tab open;
4. destination success permits source closure only when selected;
5. unsupported sockets are listed and skipped;
6. USB disconnect/reconnect produces an actionable error and recovers after refresh;
7. portable ZIP launches with PowerShell 7.2+ and Android platform tools, without a separately installed .NET application runtime.

## Security and privacy

- local-only data by default;
- no telemetry SDK;
- strict URL and input validation;
- loopback bridge, short-lived random token, rotation, rate limits, bounds, and no secret logs;
- explicit Android package queries;
- no browser profile database scraping;
- release keys only in protected environment secrets;
- minimum `GITHUB_TOKEN` permissions;
- wrapper and dependency integrity validation;
- sanitized diagnostic export only.

## CI and release gates

Pull-request CI must pass:

- synchronized versions;
- committed wrapper validation;
- executable core checks;
- static project validator;
- performance-budget configuration validation;
- PowerShell parser and Desktop Link safety contracts;
- connector JavaScript checks;
- Android unit tests;
- Android lint;
- debug APK assembly;
- artifact/report upload.

Release adds protected approval, persistent signing material, fingerprint matching, signed APK/AAB verification, deterministic packaging, manifest provenance, checksums, attestations, tag-to-commit verification, and GitHub Release publication.

## Delivery sequence

1. Merge this reliability/security/provenance tranche after green CI.
2. Configure the protected release environment and persistent signing identity.
3. Produce the signed v1.0.0 candidate.
4. Capture device benchmark results and approve a performance baseline.
5. Continue adaptive accessibility and connector UX work as focused PRs.
6. Evaluate and implement a packaged Windows UI without weakening the verified PowerShell fallback.

## PR completion checklist

- [x] README documentation index repaired without truncating existing sections
- [x] durable session-scoped source identity implemented and tested
- [x] first-seen time removed from identity equality
- [x] typed backup classification implemented
- [x] rejected backups cannot fall through to URL extraction
- [x] bridge restricted to loopback
- [x] LAN mode disabled until authenticated encrypted transport exists
- [x] release manifest bound to source commit/tag/signing fingerprint
- [x] APK and AAB fingerprints compared with approved certificate
- [x] measurable performance budgets checked in
- [x] Windows safety/portability contracts checked in
- [x] critical invariants extracted into ADRs
- [ ] PR CI green on the final implementation commit
- [ ] protected release environment and owner signing secrets configured
- [ ] signed v1.0.0 APK/AAB built, verified, attested, and published

## Readiness verdict

The source is ready for review only after the final PR commit obtains a green CI run. Production publication remains gated by the protected release environment, persistent owner signing secrets, approved certificate fingerprint, and a successful signed release workflow. No time-sensitive claim in this document substitutes for those immutable checks.
