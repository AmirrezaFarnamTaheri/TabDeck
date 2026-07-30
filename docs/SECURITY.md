# TabDeck v1 security and privacy review

## Protected data

A tab inventory can reveal health, finances, identity, employment, politics, travel, relationships, and private communications. URLs, titles, notes, tags, source devices, source/native groups, launch decks, and saved views are sensitive local data.

## Storage and telemetry

- Room and DataStore remain inside the Android application sandbox.
- Android cloud/device backup is disabled in the manifest.
- No analytics, crash-reporting, advertising, account, or cloud-sync SDK is bundled.
- No root, AccessibilityService scraping, VPN interception, notification scraping, or browser-profile database access.
- Package visibility is narrowly declared with `<queries>`; `QUERY_ALL_PACKAGES` is not requested.
- Export is user-triggered through the Storage Access Framework.

Official grounding:

- Android sandbox/privacy: <https://developer.android.com/privacy>
- Package visibility and least visibility: <https://developer.android.com/training/package-visibility/declaring>

## Export boundary

Full backup JSON and readable exports are plaintext. The backup excludes the live bridge token/session but still contains sensitive tab metadata.

- Markdown and bookmarks HTML escape user-controlled text.
- CSV quotes cells and prefixes text whose first non-space character is `=`, `+`, `-`, or `@` to reduce spreadsheet formula execution risk.
- Readable exports exclude Trash.
- Bookmarks export writes only validated HTTP(S) URLs.

Users remain responsible for storage, synchronization, and sharing of exported files.

## Import boundary

Every source is untrusted, including browser extensions and local companion tools.

Controls:

- HTTP(S) URLs only;
- no URL credentials;
- strict authority and port validation;
- control-character rejection;
- IDN/default-port/query canonicalization;
- bounded request/document/tab/title/group/note/tag/device/source-ID fields;
- aggregate intent content cap of 16 MiB, 128 shared items, and 25,000 URLs;
- valid UTF-8 prefix preservation at truncation boundaries;
- timestamp and counter coercion;
- source-identity coalescing;
- strict backup format/version checks;
- future-version and unrelated JSON rejection;
- invalid rule/group isolation;
- RE2/J rule engine.

## Local bridge threat model

Threats:

- token guessing or disclosure;
- cross-site browser requests;
- public-network exposure;
- request flooding/slow clients;
- oversized or malformed HTTP/UTF-8/JSON;
- stale listeners;
- timing comparison leakage;
- accidental long-running foreground service.

Controls:

- random 32-byte token rendered as 64 hex characters;
- constant-time token comparison;
- foreground notification and explicit stop;
- 5–120-minute auto-expiry;
- loopback-only default;
- private/link-local IPv4/IPv6 allowlisting for LAN scope;
- strict extension/loopback Origin policy;
- bounded request line, headers, body, sockets, and read time;
- JSON content-type requirement;
- per-client rate limiting and stale-client pruning;
- request IDs and success/rejection counters;
- token rotation;
- no extension token persistence unless the user explicitly opts in;
- connector health preflight sends no tab data;
- `Service.onTimeout` stops the bridge on Android 15+.

Android 15+ imposes a shared six-hour/24-hour background limit on `dataSync` foreground services. TabDeck's maximum user session is two hours and the timeout callback stops the service. Official reference: <https://developer.android.com/develop/background-work/services/fgs/timeout>

The LAN bridge uses cleartext HTTP for local browser-extension and ADB interoperability. The token provides authorization, not transport confidentiality. LAN scope should be used briefly on a trusted network.

## Android transfer boundary

- Only validated HTTP(S) URLs are launched.
- Destination package is explicit and narrowly declared.
- Runs are capped, paced, confirmed, observable, and cancellable.
- `ActivityNotFoundException` and other launch failures are counted.
- Dispatch success does not prove destination rendering or grouping.
- Partial outcomes are committed even after cancellation.

## Duplicate/destructive boundary

- Import preserves duplicates for review.
- Dedupe separates analysis from apply.
- Metadata merge is deterministic.
- App-side cleanup moves copies to Trash.
- Permanent deletion requires explicit confirmation.
- Firefox/Chromium connector cleanup is local to that browser, separate from capture, previewed, capped/chunked, and confirmed.

## Desktop Link

- Requires Windows, PowerShell 7.2+, `adb.exe`, user-enabled Developer options/USB debugging, and host authorization.
- Uses only visible DevTools sockets and temporary dynamic forwards.
- Caps live open/close operations at 250 and bridge pushes at 25,000.
- Confirms transfer and close.
- Closes a source only after destination target creation is confirmed.
- Times out ADB calls and removes forwards on refresh/window close.
- Never stores the bridge token.
- Cannot bypass a browser that exposes no DevTools socket.

Official Chrome reference: <https://developer.chrome.com/devtools/docs/remote-debugging>

## Distribution checklist

1. Publish an accurate privacy policy for local tab metadata.
2. Keep AccessibilityService collection excluded unless the product becomes a genuine accessibility tool.
3. Review foreground-service declarations and user-visible purpose against current Play policy.
4. Test Android 13+ notification permission and Android 15+ service timeout behavior.
5. Sign extensions through official channels.
6. Produce SBOM/dependency/static-security scans for the release commit.
7. Run backup/export fixtures through external parsers and browsers.
8. Perform device-level accessibility and performance testing in release mode.
