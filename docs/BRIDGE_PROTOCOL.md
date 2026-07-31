# TabDeck bridge protocol v3

Default port: `48721`

## Trust boundary and lifecycle

The bridge exists only during an active, foreground-visible, time-bounded session started by the user. It binds exclusively to loopback and accepts only loopback clients.

Canonical endpoint:

- same device or explicit ADB forward: `http://127.0.0.1:48721/api/v3/import`

Direct LAN exposure is disabled. Private IP filtering and browser `Origin` checks are not peer authentication and do not provide transport confidentiality. Any future LAN mode requires explicit opt-in, TLS with peer authentication, peer/address allowlisting, certificate lifecycle management, and immediate token/peer revocation.

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
- `identityVersion` must be exactly `1` before complete-snapshot reconciliation. Unknown versions are rejected or safely downgraded before reconciliation.

## Import request

```http
POST /api/v3/import HTTP/1.1
Content-Type: application/json
X-TabDeck-Token: <64-hex-character token>
X-TabDeck-Request-Id: <client-generated id>
```

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
