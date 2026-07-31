#!/usr/bin/env python3
"""Check or synchronize TabDeck public versions without changing compatibility formats."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

from versioning import ROOT, load_version

EXTENSION_MANIFESTS = [
    ROOT / "extensions/firefox-android/manifest.json",
    ROOT / "extensions/chromium-desktop/manifest.json",
]


def check(tag: str | None) -> list[str]:
    """Record a version-contract failure when the condition is false."""
    version = load_version()
    errors: list[str] = []

    app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for token in (
        'rootProject.file("version.properties")',
        'versionCode = productVersionCode',
        'versionName = productVersionName',
    ):
        if token not in app_build:
            errors.append(f"Android build does not derive its public version from version.properties: missing {token}")

    hardcoded = re.findall(r"\bversion(?:Code|Name)\s*=\s*(?:\"[^\"]+\"|\d+)", app_build)
    if hardcoded:
        errors.append(f"Android build contains hard-coded public version assignments: {', '.join(hardcoded)}")

    for path in EXTENSION_MANIFESTS:
        manifest = json.loads(path.read_text(encoding="utf-8"))
        actual = manifest.get("version")
        if actual != version.name:
            errors.append(f"{path.relative_to(ROOT)} version is {actual!r}; expected {version.name!r}")

    if tag is not None and tag != version.tag:
        errors.append(f"Release tag {tag!r} does not match expected {version.tag!r}")

    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    if f"## [{version.name}] - {version.release_date}" not in changelog:
        errors.append("CHANGELOG.md lacks the exact current version/date heading")

    release_notes = (ROOT / "docs/RELEASE_NOTES.md").read_text(encoding="utf-8")
    if f"v{version.name}" not in release_notes:
        errors.append("docs/RELEASE_NOTES.md does not mention the current public version")

    return errors


def sync_extensions() -> None:
    """Synchronize extension manifest versions with the product version."""
    version = load_version()
    for path in EXTENSION_MANIFESTS:
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["version"] = version.name
        path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> int:
    """Run the command-line entry point and return its exit status."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", help="Require an exact v<VERSION_NAME> release tag")
    parser.add_argument("--sync-extensions", action="store_true", help="Set both extension manifest versions")
    args = parser.parse_args()

    try:
        if args.sync_extensions:
            sync_extensions()
        version = load_version()
        errors = check(args.tag)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print(f"PASS: TabDeck {version.tag}; Android versionCode {version.code}; extensions synchronized")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
