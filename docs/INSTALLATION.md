# Installation

## Android application

### Install a published APK

1. Download `TabDeck-v1.0.0.apk` and `TabDeck-v1.0.0-SHA256.txt` from the matching GitHub Release.
2. Verify the APK checksum before installing.
3. On Android, allow installation from the file manager or browser used to open the APK when prompted.
4. Install the APK and complete the in-app capability guide.

The release AAB is for application-store or managed-distribution pipelines; it is not directly installable on a device.

### Build from source

Requirements:

- JDK 17
- Android SDK platform 36
- Android build-tools 36.0.0
- Python 3
- Node.js for extension syntax validation

```bash
./bootstrap-wrapper.sh
python3 tools/check_version.py
bash tools/run_core_checks.sh
python3 tools/validate_project.py
./gradlew clean test lintDebug assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Firefox Android connector

The unsigned XPI is a development artifact. Installation support differs by Firefox Android channel and release. The connector needs access to tabs and optional permission for the exact bridge endpoint selected by the user.

1. Start a TabDeck bridge session from **Control room**.
2. Note the endpoint and token.
3. Install the XPI using a Firefox Android development/add-on path supported by the chosen channel.
4. Open the connector, enter the endpoint and token, and use **Test bridge** before sending tabs.
5. Review the snapshot summary before capture or cleanup.

The connector cannot bypass Firefox permissions or unsupported mobile extension APIs.

## Chromium desktop connector

1. Extract `TabDeck-v1.0.0-Chromium-Bridge.zip`.
2. Open the browser's extensions page.
3. Enable developer mode.
4. Choose **Load unpacked** and select the extracted folder.
5. Start a TabDeck LAN bridge session, enter its private-network endpoint and token, and run **Test bridge**.

This connector captures desktop Chromium-family tabs and can send their native desktop group metadata to the Android inventory. It is not an Android Chrome extension.

## Windows Android Desktop Link

Requirements:

- Windows PowerShell/WPF
- Android platform-tools with `adb.exe`
- USB debugging explicitly enabled and authorized by the device owner
- A supported Android Chromium build exposing a DevTools socket

Extract `TabDeck-v1.0.0-Desktop-Link.zip`, read its included README, and run `Start-TabDeckLink.cmd`. The companion does not enable developer options, authorize the computer, or bypass browser debugging restrictions.

## Upgrade and data safety

Before upgrading, create a full JSON backup from **Control room**. Product version numbers do not replace internal format versions; existing Room, backup, query, and bridge compatibility identifiers are preserved deliberately.
