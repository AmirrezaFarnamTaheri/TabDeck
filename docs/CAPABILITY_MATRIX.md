# Android browser capability matrix

## Ground rule

Android application sandboxing prevents a normal unrelated app from opening another browser's private database. Package visibility can reveal whether a declared browser package is installed; it does not grant tab access. An explicit URL intent can ask an installed browser to open a URL; it does not grant source-tab closure or native group creation.

## Capture routes

| Browser/channel | Android package | Direct Android snapshot in this project | Share/import | Desktop Link possibility | Explicit transfer target |
|---|---|---:|---:|---:|---:|
| Chrome | `com.android.chrome` | No | Yes | Build/device-dependent | Yes |
| Chrome Beta | `com.chrome.beta` | No | Yes | Build/device-dependent | Yes |
| Chrome Dev | `com.chrome.dev` | No | Yes | Build/device-dependent | Yes |
| Chrome Canary | `com.chrome.canary` | No | Yes | Build/device-dependent | Yes |
| Firefox | `org.mozilla.firefox` | Firefox extension | Yes | Not required | Yes |
| Firefox Beta | `org.mozilla.firefox_beta` | Firefox extension, API/build-dependent | Yes | Not required | Yes |
| Firefox Nightly | `org.mozilla.fenix` | Firefox extension | Yes | Not required | Yes |
| Opera | `com.opera.browser` | No | Yes | Build-dependent | Yes |
| Opera Beta | `com.opera.browser.beta` | No | Yes | Build-dependent | Yes |
| Brave | `com.brave.browser` | No | Yes | Build-dependent | Yes |
| Brave Beta | `com.brave.browser_beta` | No | Yes | Build-dependent | Yes |
| Microsoft Edge | `com.microsoft.emmx` | No | Yes | Build-dependent | Yes |
| Edge Beta | `com.microsoft.emmx.beta` | No | Yes | Build-dependent | Yes |
| Vivaldi | `com.vivaldi.browser` | No | Yes | Build-dependent | Yes |
| Samsung Internet | `com.sec.android.app.sbrowser` | No | Yes | Unverified/not assumed | Yes |
| DuckDuckGo | `com.duckduckgo.mobile.android` | No | Yes | No assumption | Yes |
| Tor Browser | `org.torproject.torbrowser` | No bundled connector | Yes | No assumption | Yes |

“Build-dependent” means Desktop Link proceeds only when the browser exposes a Chromium DevTools remote socket to an already authorized ADB host. Absence of a socket is a supported outcome, not an error TabDeck can bypass.

## Operation truth table

| Operation | Android app | Firefox extension | Desktop Link |
|---|---:|---:|---:|
| Read a supplied/share URL | Yes | Yes | Yes |
| Read all tabs visible to connector | No universal API | Yes, within extension permissions | Yes, within exposed DevTools targets |
| Preserve title | Route-dependent | Yes | Yes |
| Preserve pinned state | Route-dependent | Yes | DevTools target does not guarantee it |
| Preserve source grouping | TabDeck metadata | API-dependent | Source socket metadata; not native Android groups |
| Deduplicate local inventory | Yes | Preview/cleanup inside Firefox | Select duplicate live targets |
| Close browser tabs | No | User-triggered extension cleanup where API permits | Yes, explicit confirmation and exposed target only |
| Open in another Android browser | Yes, explicit intent | Via TabDeck | Yes, exposed DevTools destination |
| Force destination-native tab groups | No | No cross-browser guarantee | No guarantee |

## Deliberately excluded

- Root-only browser database access
- AccessibilityService scraping
- VPN/TLS interception
- Notification scraping
- hidden/private browser APIs
- automatic USB-debugging enablement
- silent destructive operations

These exclusions reduce reach, but materially improve security, Play-policy viability, and user trust.


## Official references

- Android privacy/sandbox: <https://developer.android.com/privacy>
- Android package visibility: <https://developer.android.com/training/package-visibility>
- Android URL/package interaction use cases: <https://developer.android.com/training/package-visibility/use-cases>
- Firefox Android extension development: <https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/>
- Chrome Android remote debugging: <https://developer.chrome.com/devtools/docs/remote-debugging>
