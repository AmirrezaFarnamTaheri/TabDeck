# TabDeck bridge protocol v3

Default port: `48721`

## Trust boundary and lifecycle

The bridge exists only during an active, foreground-visible, time-bounded session started by the user. It binds exclusively to loopback and accepts only loopback clients.

Canonical endpoint:

- same device or explicit ADB forward: `http://127.0.0.1:48721/api/v3/import`

Direct LAN exposure is disabled. Private IP filtering and browser `Origin` checks are not peer authentication and do not provide transport confidentiality. Any future LAN mode requires explicit opt-in, authenticated TLS, peer/address allowlisting, certificate lifecycle management, and immediate token/peer revocation.

Compatibility routes `/api/v1/import` and `/api/v2/import` pass through the same current parser and validation boundary.

## Health preflight

```http
GET /health HTTP/1.1
Host: 127.0.0.1:48721
Accept: application/json
```

Representative response:

```json
{
  "ok": true,
  "service": "TabDeck Bridge",
  "version": 3,
  "scope": "THIS_DEVICE",
  "expiresAtEpochMs": 1785400300000,
  "requestId": "f09c1a4d"
}
```

The connectors expose a **Test bridge** action that calls `/health` and sends no tab inventory.

## Session-scoped source identity

Browser tab IDs are unique only within a browser runtime and may be reused after restart. Connector payloads therefore include `sourceSessionId`.

TabDeck persists its own UUID and a bounded opaque source ID derived from the session and external tab ID. The raw session ID is not stored. `firstSeenAt` is metadata only and is not part of identity equality.

- retries in the same session resolve to the same source identity;
- reused tab IDs in a later session resolve to a different identity;
- legacy payloads remain importable but cannot authorize destructive complete-snapshot reconciliation;
- `identityVersion` is currently `1`.

## Import request

```http
POST /api/v3/import HTTP/1.1
Content-Type: application/json
X-TabDeck-Token: <64-hex-character token>
X-TabDeck-Request-Id: <client-generated id>
```

```json
{
  "browser": "Firefox Nightly",
  "completeSnapshot": true,
  "sourceSessionId": "41a26c82-8c7b-4cad-b6ad-526f067b80d9",
  "identityVersion": 1,
  "sourceLabel": "Firefox Nightly Android",
  "deviceName": "Pixel",
  "capturedAt": 1785400000000,
  "tabs": [
    {
      "id": "browser-tab-123",
      "deviceId": "Pixel",
      "url": "https://developer.android.com/",
      "title": "Android Developers",
      "group": "Research",
      "pinned": true,
      "createdAt": 1785399000000,
      "lastSeenAt": 1785400000000
    }
  ]
}
```

## Source reconciliation

Canonical source equality is derived from connector/device/browser/profile/session/window/external-tab attributes. A later retry updates the same local record while preserving user-owned group, notes, tags, lifecycle state, and transfer counters.

`completeSnapshot=true` is honored only when browser, device, source session, and every tab ID are provable. A complete zero-tab payload is valid. Partial/current-window/protected-pinned snapshots must set `completeSnapshot=false`.

## Validation and limits

- active, unexpired session;
- loopback client only;
- strict Origin policy for extension and loopback origins;
- JSON content type for imports;
- constant-time token comparison;
- bounded request target, headers, body, sockets, timeouts, text fields, and tab count;
- strict UTF-8;
- maximum 25,000 tabs per request;
- HTTP(S) URLs only;
- invalid tab entries rejected individually;
- duplicate source IDs coalesced deterministically;
- per-client/session rate limit;
- short server request ID in every JSON response.
