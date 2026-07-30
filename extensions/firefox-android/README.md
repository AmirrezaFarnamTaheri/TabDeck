# TabDeck Bridge — Firefox Android

A user-triggered Firefox WebExtension that snapshots tabs visible through Firefox's extension API and sends them to a timed, token-authenticated TabDeck bridge.

## Features

- Firefox / Firefox Beta / Firefox Nightly source labels
- device/profile label and stable source identity
- current-window or all-window scope
- live transferable/duplicate/window counts
- normalized duplicate preview
- explicit duplicate cleanup with pinned-tab protection
- title, URL, pin state, source/window metadata
- private/loopback endpoint validation
- bridge health preflight with API version and session-expiry feedback
- token persistence only when **Remember token** is explicitly enabled

## Development install

1. Start TabDeck → **Connect** → **This device only** bridge.
2. Copy the loopback endpoint and token.
3. Load the unsigned extension temporarily through Firefox's supported Android extension-debugging workflow, or package/sign it through Mozilla Add-ons for normal distribution.
4. Open the popup, choose the Firefox channel/scope, inspect counts, and send.

An unsigned development XPI is not a production-signed add-on. Firefox Android API availability varies by channel/version; test every declared target before release.
