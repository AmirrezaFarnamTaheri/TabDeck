# TabDeck Desktop Link for Windows

Desktop Link is an optional Android-control companion. It uses an already authorized ADB connection and Chromium's exposed DevTools HTTP targets; it does not read browser profile databases or enable debugging.

## Capabilities

- discover authorized devices and up to 32 visible `*devtools_remote*` sockets
- allocate dynamic local forward ports
- read live page targets from `/json`
- search and select visible targets
- select normalized duplicate copies while retaining one survivor
- open selected URLs in another exposed Android Chromium target with `/json/new`
- optionally close only source targets whose destination creation was confirmed
- explicitly close selected live targets after confirmation
- push up to 25,000 selected targets to a device-local TabDeck bridge through ADB forwarding
- export selected targets as JSON or URL text

## Safety limits

- 20-second ADB timeout
- maximum 250 live open/close targets per run
- confirmation before transfer or close
- temporary forward cleanup on device refresh/window close
- no token storage
- no operation when the browser exposes no DevTools socket

## Requirements

- Windows 10/11
- PowerShell 7.2+
- current Android Platform Tools (`adb.exe`)
- Developer options + USB debugging enabled by the user
- host authorization accepted on the device
- a Chromium browser build that exposes a DevTools remote socket

Run:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -Sta -File .\TabDeckLink.ps1
```

The destination/source labels are DevTools socket names, not a guaranteed mapping to native Android tab groups. Validate target browser behavior on the exact device/build before destructive use.
