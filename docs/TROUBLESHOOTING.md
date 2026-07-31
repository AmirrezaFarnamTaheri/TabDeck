# Troubleshooting

## The app cannot see all open tabs in another Android browser

This is an Android platform boundary, not a missing permission. Use browser sharing, Firefox Android connector support, file import, or Desktop Link for a debuggable Chromium target. Root, private browser databases, notification scraping, and AccessibilityService extraction are intentionally unsupported.

## A destination browser is not listed

- Confirm the exact browser variant is installed and enabled.
- Open **Capture** and refresh available open targets.
- Some browsers can still receive a standard Android URL intent even if they are not among TabDeck's declared optimized targets.
- Work-profile or secondary-user installations may not be visible to the current profile.

## Transfer opens only some tabs

Check Open history for dispatched and failed request counts. TabDeck requests a new tab for every valid URL, but the browser owns final rendering and may be disabled, removed, background-restricted, overloaded, or reject a URL. Use gentler pacing or retry failed items.

## Bridge test fails

- Confirm the bridge is visibly running and has not expired.
- Confirm endpoint, port, and 64-hex token.
- For same-device/ADB forwarding, use loopback.
- Rotate the token after accidental disclosure.

All non-loopback addresses are rejected by design. Desktop Link reaches loopback through an explicit ADB forward.

## Firefox connector cannot enumerate tabs

Firefox Android extension APIs and installation paths vary by channel. Confirm the add-on is installed in the intended Firefox variant, required permissions were granted, and the current channel supports the used tabs APIs. Special/internal pages may not expose transferable URLs.

## Desktop Link finds no DevTools target

- Confirm `adb devices` shows one authorized device.
- Confirm USB debugging was explicitly authorized on the phone.
- Confirm the browser process exposes a `*devtools_remote*` socket.
- Some Android browser builds do not expose one; Desktop Link cannot force-enable it.
- Close stale forwards and reconnect the device before retrying.

## Import detects fewer links than expected

TabDeck accepts only HTTP/HTTPS URLs and rejects credentials, malformed authorities, invalid ports, control characters, unsupported schemes, and documents beyond the explicit byte-safety boundary. It does not discard later valid links because an item-count ceiling was reached. Review the import preview and source format.

## A full backup will not restore

TabDeck rejects unrelated JSON and unsupported future backup versions rather than silently importing nothing. Confirm the file is a TabDeck backup, is valid UTF-8 JSON, and uses a supported format version. Keep the original backup unchanged while diagnosing.

## Build cannot download Gradle or Android dependencies

Verify DNS, proxy, certificate interception, and access to Gradle, Google Maven, Maven Central, and Android SDK endpoints. Enterprise environments may require approved mirrors. Do not disable checksum or TLS verification to work around network policy.

## Release workflow reports a version mismatch

Run:

```bash
python3 tools/check_version.py --tag v1.1.0
```

Then synchronize `version.properties`, extension manifests, changelog, release notes, and the Git tag. Do not reset Room/backup/bridge/query compatibility versions.
