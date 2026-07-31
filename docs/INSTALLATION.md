# Installation

## Android application

### Install the GitHub Release APK

1. Download `TabDeck-v1.1.0.apk` and `TabDeck-v1.1.0-SHA256.txt` from the same GitHub Release.
2. Verify the APK checksum before installing.
3. Optionally confirm the APK signing-certificate SHA-256 is `8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7`.
4. On Android, allow installation from the file manager or browser used to open the APK when prompted.
5. Install the APK and complete the in-app capability guide.

TabDeck publishes an APK directly through GitHub. No application store, account, or cloud service is required.

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
python3 tools/validate_simple_release.py
./gradlew clean test lintDebug assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Firefox Android connector

The unsigned XPI is a development artifact. Installation support differs by Firefox Android channel and release.

1. Start a TabDeck bridge session from **Capture**.
2. Use the loopback endpoint and token shown by TabDeck.
3. Install the XPI using a Firefox Android development/add-on path supported by the chosen channel.
4. Open the connector, enter the endpoint and token, and use **Test bridge** before sending tabs.
5. Review the snapshot summary before capture or cleanup.

The connector cannot bypass Firefox permissions or unsupported mobile extension APIs.

## Chromium desktop connector

1. Extract `TabDeck-v1.1.0-Chromium-Bridge.zip`.
2. Open the browser's extensions page.
3. Enable developer mode.
4. Choose **Load unpacked** and select the extracted folder.
5. Connect the Android device and authorize ADB.
6. Run:

```bash
adb forward tcp:48721 tcp:48721
```

7. Start the TabDeck bridge and enter `http://127.0.0.1:48721/api/v3/import` plus the current token in the extension.
8. Run **Test bridge**, then send the reviewed session.

The bridge is loopback-only. Direct LAN/private-IP access is not supported.

## Windows Android Desktop Link

Requirements:

- Windows PowerShell 7.2+
- Android platform-tools with `adb.exe`
- USB debugging explicitly enabled and authorized by the device owner
- A supported Android Chromium build exposing a DevTools socket

Extract `TabDeck-v1.1.0-Desktop-Link.zip`, read its included README, and run `Start-TabDeckLink.cmd`. The companion does not enable developer options, authorize the computer, or bypass browser debugging restrictions.

## Upgrade and data safety

Community GitHub APKs use the same repository-published signing key, so a later version can upgrade an earlier community build. Before upgrading, create a full JSON backup from **Capture**. Product version numbers do not replace internal format versions; existing Room, backup, query, and bridge compatibility identifiers are preserved deliberately.
