# TabDeck bridge protocol v3

Default port: `48721`

## Lifecycle

The bridge exists only during an active, foreground-visible, time-bounded session started by the user.

Endpoint examples:

- same device or ADB forward: `http://127.0.0.1:48721/api/v3/import`
- trusted LAN: `http://<phone-private-address>:48721/api/v3/import`

Compatibility routes `/api/v1/import` and `/api/v2/import` are accepted and pass through the same current parser and validation boundary.

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

The Firefox and Chromium connectors expose a **Test bridge** action that requests optional host permission, calls `/health`, and reports API version and approximate minutes remaining. It sends no tab inventory.

## Import request

```http
POST /api/v3/import HTTP/1.1
Content-Type: application/json
X-TabDeck-Token: <64-hex-character token>
X-TabDeck-Request-Id: <client-generated id>
```

Representative payload:

```json
{
  "browser": "Firefox Nightly",
  "completeSnapshot": true,
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
      "groupColor": "blue",
      "pinned": true,
      "active": false,
      "createdAt": 1785399000000,
      "lastSeenAt": 1785400000000
    }
  ]
}
```

The top-level browser is authoritative for source identity. Tab-level browser fields from older clients are tolerated but cannot escape the supported browser mapping.

## Source reconciliation

Source identity is:

```text
(source device, source browser, source tab id)
```

A later snapshot updates the same local record while preserving user-owned TabDeck group, notes, tags, status, and transfer counters.

`completeSnapshot=true` means the connector asserts that the payload represents its complete chosen scope. A complete zero-tab payload is valid. Missing source records are kept or archived according to the user's synchronization policy. Partial/current-window/protected-pinned snapshots must set `completeSnapshot=false`.

## Validation and limits

- session must be active and unexpired;
- LAN clients must be loopback/private/link-local;
- HTTP parser has bounded request line, header count/size, and body size;
- only `GET /health`, `OPTIONS`, and supported `POST` import routes exist;
- JSON content type is required for import;
- token comparison is constant-time;
- request IDs are bounded and sanitized;
- strict UTF-8 is required;
- maximum accepted tabs per request: 25,000;
- only validated HTTP(S) URLs are stored;
- invalid tab entries are rejected individually;
- duplicate source IDs within one payload are coalesced deterministically;
- request rate is limited per client/session.

Oversized requests, invalid tokens, invalid origins, unsupported methods/paths, out-of-scope clients, expired sessions, invalid UTF-8, and rate limits receive 4xx responses. Every JSON response includes a short server request ID.

## Response shape

Successful imports return at least:

```json
{
  "ok": true,
  "imported": 123,
  "rejected": 2,
  "requestId": "9a23ce10"
}
```

Clients must treat a non-2xx status as failure even if a response body cannot be parsed.
