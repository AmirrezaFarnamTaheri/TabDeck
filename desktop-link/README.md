# TabDeck Desktop Link for Windows

Desktop Link is a guided capture workspace for Android Chromium sessions exposed to an already authorized ADB host. It does not read browser profile databases, enable developer options, authorize a computer, or bypass browser debugging restrictions.

## Guided workflow

1. **Device** — refresh authorized Android devices and choose one.
2. **Browser tabs** — load visible `*devtools_remote*` sessions and inspect their page targets.
3. **Selection** — search, select, and review the exact tabs to send.
4. **Send to TabDeck** — start the app's temporary bridge, enter its token, and send the complete selection.

The workspace also supports explicit secondary actions: export selected URLs/JSON, open selected URLs in another exposed Chromium session, and close selected live targets after confirmation.

## Behavior

- Discovers every visible DevTools socket returned by the selected authorized device.
- Uses dynamic local forwarding ports and cleans temporary forwards on refresh and exit.
- Loads live page targets from `/json` and labels each target with its browser session.
- Sends every selected valid tab. Payloads are split by approximate request bytes when necessary rather than truncating by count.
- Opens selected URLs in another exposed target before optionally closing confirmed source targets.
- Never stores the bridge token.
- Treats absence of a DevTools socket as a supported browser/build limitation.

## Safety boundaries

- Per-command ADB timeouts prevent a frozen process from hanging the workspace indefinitely.
- Destructive close operations require confirmation.
- Destination creation is confirmed before optional source closure.
- The TabDeck bridge remains loopback-only, authenticated, temporary, and request-size bounded.
- There is no arbitrary tab-count ceiling for selection, export, opening, closing, or capture.

## Requirements

- Windows 10/11
- PowerShell 7.2+
- current Android Platform Tools (`adb.exe`)
- Developer options and USB debugging enabled by the device owner
- host authorization accepted on the device
- an Android Chromium build that exposes a DevTools remote socket

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -Sta -File .\TabDeckLink.ps1
```

DevTools socket labels are technical session identifiers, not guaranteed native Android browser group names. Validate destructive behavior on the exact browser build and device before closing live targets.
