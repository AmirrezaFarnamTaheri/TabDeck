#!/usr/bin/env python3
"""Apply the final PR #6 review follow-ups to a checked-out target tree."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve()


def replace_once(relative: str, old: str, new: str) -> None:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{relative}: expected exactly one source match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "extensions/chromium-desktop/README.md",
    """1. Start the loopback bridge in TabDeck. For a desktop browser, create an explicit ADB forward from the host to device port 48721.\n2. Copy the forwarded loopback endpoint and token.\n3. Open the desktop browser's extension management page, enable developer mode, and **Load unpacked** this folder.\n4. Inspect the popup summary and send the session.\n""",
    """1. Start the loopback bridge in TabDeck. For a desktop browser, create the host-to-device port forward:\n\n   ```bash\n   adb forward tcp:48721 tcp:48721\n   ```\n\n2. Use `http://127.0.0.1:48721/api/v3/import` as the forwarded loopback endpoint and copy the token.\n3. Open the desktop browser's extension management page, enable developer mode, and **Load unpacked** this folder.\n4. Inspect the popup summary and send the session.\n""",
)

for relative in (
    "extensions/chromium-desktop/popup.js",
    "extensions/firefox-android/popup.js",
):
    replace_once(
        relative,
        "// bridge-runtime.js accepts and canonicalizes /api/v2/import and /api/v3/import.\n",
        "// bridge-runtime.js accepts and canonicalizes /api/v1/import, /api/v2/import, and /api/v3/import.\n",
    )

replace_once(
    "tools/validate_project.py",
    "from typing import Iterable\n\nfrom versioning import load_version\n",
    "from typing import Iterable\nfrom urllib.parse import urlsplit\n\nfrom versioning import load_version\n",
)
replace_once(
    "tools/validate_project.py",
    """    bridge_network = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgeNetwork.kt").read_text(encoding="utf-8")\n    bridge_parser = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8")\n    checks = {\n""",
    """    bridge_network = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgeNetwork.kt").read_text(encoding="utf-8")\n    bridge_parser = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8")\n    endpoint_match = re.search(r'const val LOOPBACK_ENDPOINT\\s*=\\s*"([^"]+)"', bridge_network)\n    endpoint_host = urlsplit(endpoint_match.group(1)).hostname if endpoint_match else None\n    checks = {\n""",
)
replace_once(
    "tools/validate_project.py",
    '        "loopback-only bridge": "LOOPBACK_ENDPOINT" in bridge_network and "0.0.0.0" not in bridge,\n',
    '        "loopback-only bridge": endpoint_host in {"127.0.0.1", "localhost", "::1"} and "0.0.0.0" not in bridge,\n',
)

print("Applied final PR #6 review follow-ups")
