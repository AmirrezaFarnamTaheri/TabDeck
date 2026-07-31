#!/usr/bin/env python3
"""Validate TabDeck's secretless community APK release contract."""

from __future__ import annotations

import base64
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_CERT = "8265D1219753753DC36635BAAEAB887FE63742C93CD686A498E5B66683A704A7"


def active_yaml(text: str) -> str:
    """Return workflow text without blank lines and full-line comments."""
    return "\n".join(line for line in text.splitlines() if line.strip() and not line.lstrip().startswith("#"))


def require_tokens(text: str, tokens: tuple[str, ...], label: str) -> None:
    """Raise when any required release-contract token is absent."""
    missing = [token for token in tokens if token not in text]
    if missing:
        raise SystemExit(f"{label} missing required tokens: {', '.join(missing)}")


def forbid_tokens(text: str, tokens: tuple[str, ...], label: str) -> None:
    """Raise when a removed release mechanism remains active."""
    present = [token for token in tokens if token in text]
    if present:
        raise SystemExit(f"{label} still contains removed mechanisms: {', '.join(present)}")


def validate_branding() -> None:
    """Validate source, Android, desktop, and connector logo assets."""
    svg_paths = (
        ROOT / "branding/TabDeck-mark.svg",
        ROOT / "branding/TabDeck-lockup.svg",
        ROOT / "desktop-link/TabDeck-mark.svg",
        ROOT / "extensions/chromium-desktop/tabdeck-mark.svg",
        ROOT / "extensions/firefox-android/tabdeck-mark.svg",
    )
    for path in svg_paths:
        ET.parse(path)
    foreground = (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").read_text(encoding="utf-8")
    monochrome = (ROOT / "app/src/main/res/drawable/ic_launcher_monochrome.xml").read_text(encoding="utf-8")
    themed = (ROOT / "app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml").read_text(encoding="utf-8")
    require_tokens(foreground, ("#0F766E", "#14B8A6", "#F59E0B", "#F8FAFC"), "launcher foreground")
    require_tokens(monochrome, ("<vector", "#FFFFFF"), "launcher monochrome")
    require_tokens(themed, ("@drawable/ic_launcher_foreground", "@drawable/ic_launcher_monochrome"), "themed launcher")


def validate_public_key() -> None:
    """Validate the repository-published community keystore material and identity."""
    encoded = (ROOT / "release/tabdeck-community.jks.base64").read_text(encoding="ascii").strip()
    decoded = base64.b64decode(encoded, validate=True)
    if len(decoded) < 2000:
        raise SystemExit("Community keystore payload is implausibly small")
    documentation = (ROOT / "release/README.md").read_text(encoding="utf-8")
    require_tokens(documentation, (EXPECTED_CERT, "intentionally public", "no GitHub environments or secrets"), "community key documentation")


def main() -> int:
    """Run all simple-release and branding checks."""
    release = active_yaml((ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8"))
    ci = active_yaml((ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8"))
    packager = (ROOT / "tools/package_release.py").read_text(encoding="utf-8")

    require_tokens(
        release,
        (
            "permissions:\n  contents: write",
            "Build community release APK",
            "assembleRelease",
            "apksigner",
            "release/tabdeck-community.jks.base64",
            EXPECTED_CERT,
            "--require-apk",
            "gh release create",
        ),
        "release workflow",
    )
    forbid_tokens(
        release,
        (
            "secrets.",
            "environment: release",
            "bundleRelease",
            ".aab",
            "actions/attest",
            "id-token:",
            "attestations:",
            "TABDECK_RELEASE_CERT_SHA256",
        ),
        "release workflow",
    )
    require_tokens(ci, ("tools/validate_simple_release.py", "tools/package_release.py"), "CI workflow")
    require_tokens(
        packager,
        (
            "--require-apk",
            "TabDeck-mark.svg",
            "TabDeck-lockup.svg",
            "github-community-release",
            "app/build/outputs/apk/release/app-release.apk",
        ),
        "release packager",
    )
    if re.search(r"app-release\.aab|bundle/release", packager):
        raise SystemExit("Release packager still contains an AAB/store path")

    validate_public_key()
    validate_branding()
    print("Secretless branded GitHub release contracts passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
