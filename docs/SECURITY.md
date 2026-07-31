# TabDeck v1 security and privacy review

## Protected data

URLs, titles, notes, tags, source devices, source groups, launch decks, and saved views can reveal highly sensitive personal activity. TabDeck keeps this data inside the Android app sandbox unless the user explicitly exports or transfers it.

## Storage and telemetry

- Room and DataStore remain sandboxed.
- Android cloud/device backup is disabled.
- No analytics, advertising, account, cloud-sync, or remote crash SDK is bundled.
- No root, AccessibilityService scraping, VPN interception, notification scraping, hidden API, or browser-profile database access.
- Package visibility is explicit; `QUERY_ALL_PACKAGES` is not requested.
- Export is user-triggered through the Storage Access Framework.

## Import boundary

Every source is untrusted. Controls include HTTP(S)-only URLs, no URL credentials, strict authority/port validation, bounded fields and counts, strict UTF-8, timestamp coercion, source-identity coalescing, RE2/J rules, and typed backup classification.

Malformed or unsupported backup-shaped input is rejected. It is never reinterpreted as a plain URL list.

## Bridge threat model

Threats include token disclosure, cross-site requests, non-local clients, request flooding, malformed HTTP/JSON, stale listeners, and accidental long-running service execution.

Controls:

- loopback-only listener and client policy;
- explicit ADB forwarding for desktop use;
- random 32-byte token and constant-time comparison;
- token rotation/revocation by stopping the session and regenerating the token;
- foreground notification, explicit stop, and 5–120-minute expiry;
- strict extension/loopback Origin policy;
- bounded request line, headers, body, sockets, and read time;
- content-type enforcement and strict UTF-8;
- rate limiting and stale-client pruning;
- no secret-bearing logs;
- Android 15+ timeout handling.

Direct LAN mode is disabled. It may return only after authenticated TLS, explicit user opt-in, peer/address allowlisting, certificate lifecycle management, and immediate revocation are implemented.

## Source identity

External tab IDs are session-scoped and may be reused. TabDeck stores an independent UUID and a one-way session-scoped opaque source ID. `firstSeenAt` is metadata only. Legacy unscoped IDs cannot authorize destructive missing-tab reconciliation.

## Community release security

The GitHub Release workflow uses a repository-published community signing key. No protected environment or GitHub Actions secret is required.

The key is intentionally public. Its purpose is Android package-signature continuity so later community APKs can upgrade earlier ones. It does not provide exclusive publisher authentication, because anyone with the repository can use it.

Users should verify:

- the download comes from the official repository release page;
- the release tag resolves to the manifest's source commit;
- every asset matches the published SHA-256 file;
- the APK certificate fingerprint is `8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`.

The workflow verifies the committed key material, APK alignment, APK signature, certificate fingerprint, source/tag binding, archive integrity, and checksums before publication. No AAB or application-store release is produced.

## Desktop Link

- user-authorized ADB only;
- recognized Chromium-family DevTools sockets only;
- unsupported sockets reported and skipped;
- temporary forwards removed on refresh and normal exit;
- destination target verified before source closure;
- source remains open when destination verification fails;
- token never stored;
- no browser database access;
- portable PowerShell fallback does not require a separately installed .NET application runtime.
