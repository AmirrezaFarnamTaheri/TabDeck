# TabDeck Bridge — Chromium desktop

A Manifest V3 desktop extension for Chrome, Chrome Beta/Dev/Canary, Brave, Opera, Edge, and Vivaldi. It is a companion capture path for desktop/synchronized sessions and can send native desktop tab-group metadata to Android TabDeck over a short-lived loopback bridge reached through an explicit ADB port forward.

## Features

- all-window tab snapshot
- native tab-group title/color lookup when available
- live tab/group/duplicate counts
- normalized duplicate preview and explicit cleanup
- source channel/device label
- loopback endpoint validation
- bridge health preflight with API version and session-expiry feedback
- token persistence only after explicit opt-in

## Load unpacked

1. Start the loopback bridge in TabDeck. For a desktop browser, create the host-to-device port forward:

   ```bash
   adb forward tcp:48721 tcp:48721
   ```

2. Use `http://127.0.0.1:48721/api/v3/import` as the forwarded loopback endpoint and copy the token.
3. Open the desktop browser's extension management page, enable developer mode, and **Load unpacked** this folder.
4. Inspect the popup summary and send the session.

Chrome-family Android browsers do not support installing normal Chrome extensions. Use Android Share/export or Windows Desktop Link for Android Chrome-family tabs.