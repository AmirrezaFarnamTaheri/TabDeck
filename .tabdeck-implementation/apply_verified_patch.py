#!/usr/bin/env python3
"""Apply the checksum-bound TabDeck implementation payload to the PR worktree."""

from __future__ import annotations

import gzip
import hashlib
from pathlib import Path
import shutil
import subprocess

ROOT = Path.cwd()
PAYLOAD_DIR = ROOT / ".tabdeck-implementation"
EXPECTED_SHA256 = "301e6687d39ffb9f415d31ab1ab336f8c24163cc2c0fa132737ef76998495d1a"
PATCH_PATH = Path("/tmp/tabdeck-complete.patch")


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"replacement expected once, found {count}: {old[:100]!r}")
    return text.replace(old, new, 1)


def reconstruct_patch() -> None:
    chunks = sorted(PAYLOAD_DIR.glob("patch-*.b64"))
    if len(chunks) != 6:
        raise SystemExit(f"expected 6 payload chunks, found {len(chunks)}")

    import base64

    encoded = "".join(path.read_text(encoding="utf-8") for path in chunks)
    compressed = base64.b64decode(encoded, validate=True)
    actual = hashlib.sha256(compressed).hexdigest()
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"payload checksum mismatch: {actual}")

    patch = gzip.decompress(compressed).decode("utf-8")
    marker = "diff --git a/tools/validate_project.py b/tools/validate_project.py\n"
    start = patch.find(marker)
    if start < 0:
        raise SystemExit("validate_project.py patch section was not found")
    next_section = patch.find("\ndiff --git ", start + len(marker))
    patch = patch[:start] if next_section < 0 else patch[:start] + patch[next_section + 1 :]
    PATCH_PATH.write_text(patch, encoding="utf-8")


def apply_patch() -> None:
    for relative in (
        "README.md",
        "docs/DEEP_IMPROVEMENT_PLAN.md",
        "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt",
        "app/src/main/java/com/tabdeck/app/engine/SourceIdentity.kt",
        "app/src/test/java/com/tabdeck/app/engine/SourceIdentityTest.kt",
        "extensions/chromium-desktop/popup.js",
        "extensions/firefox-android/popup.js",
        "tools/CoreChecks.kt",
        "tools/run_core_checks.sh",
        "docs/ARCHITECTURE.md",
        "docs/BRIDGE_PROTOCOL.md",
    ):
        (ROOT / relative).unlink(missing_ok=True)

    run("git", "apply", "--check", str(PATCH_PATH))
    run("git", "apply", str(PATCH_PATH))


def reconcile_validator() -> None:
    path = ROOT / "tools/validate_project.py"
    text = path.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '    export_codec = (ROOT / "app/src/main/java/com/tabdeck/app/data/TabExportCodec.kt").read_text(encoding="utf-8")\n',
        '    export_codec = (ROOT / "app/src/main/java/com/tabdeck/app/data/TabExportCodec.kt").read_text(encoding="utf-8")\n'
        '    snapshot_codec = (ROOT / "app/src/main/java/com/tabdeck/app/data/SnapshotJsonCodec.kt").read_text(encoding="utf-8")\n'
        '    bridge_network = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgeNetwork.kt").read_text(encoding="utf-8")\n',
    )
    text = replace_once(
        text,
        '        "UTF-8 bounded share import": "fun utf8Prefix" in view_model and "MAX_IMPORT_DOCUMENT_BYTES" in view_model,\n',
        '        "UTF-8 bounded share import": "fun utf8Prefix" in view_model and "MAX_IMPORT_DOCUMENT_BYTES" in view_model,\n'
        '        "typed backup classification": "sealed interface DecodeResult" in snapshot_codec and "decodeClassified" in view_model,\n'
        '        "loopback-only bridge": "LOOPBACK_ENDPOINT" in bridge_network and "0.0.0.0" not in bridge,\n'
        '        "session-scoped source identity": "SourceIdentity.encodeTabId" in (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8"),\n',
    )
    text = replace_once(
        text,
        '        if "testBridgeConnection" not in popup or "\'/health\'" not in popup:\n'
        '            fail(f"Extension bridge preflight is missing in {folder.relative_to(ROOT)}")\n'
        '    ok("Validated TabDeck v1 product, compatibility, bridge, paging, export, bulk-control, and widget contracts")\n',
        '        if "testBridgeConnection" not in popup or "\'/health\'" not in popup:\n'
        '            fail(f"Extension bridge preflight is missing in {folder.relative_to(ROOT)}")\n'
        '        if "sourceSessionId" not in popup or "getSourceSession" not in popup:\n'
        '            fail(f"Extension session-scoped tab identity is missing in {folder.relative_to(ROOT)}")\n'
        '    parser = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8")\n'
        '    identity = (ROOT / "app/src/main/java/com/tabdeck/app/engine/SourceIdentity.kt").read_text(encoding="utf-8")\n'
        '    if "SourceIdentity.encodeTabId" not in parser or "sourceSessionId" not in parser:\n'
        '        fail("Bridge parser is missing session-scoped source identity")\n'
        '    if "sid1:" not in identity or "isSessionScoped" not in identity:\n'
        '        fail("Source identity codec contract is incomplete")\n'
        '    for required_file in (\n'
        '        ROOT / "tools/performance_budget.py",\n'
        '        ROOT / "tools/performance-budgets.json",\n'
        '        ROOT / "desktop-link/Test-TabDeckLink.ps1",\n'
        '        ROOT / "docs/adr/0001-durable-source-identity.md",\n'
        '        ROOT / "docs/adr/0002-loopback-bridge-trust-boundary.md",\n'
        '        ROOT / "docs/adr/0003-release-provenance.md",\n'
        '    ):\n'
        '        if not required_file.is_file():\n'
        '            fail(f"Missing verification contract: {required_file.relative_to(ROOT)}")\n'
        '    ok("Validated TabDeck v1 product, compatibility, bridge, paging, export, bulk-control, widget, and source-identity contracts")\n',
    )
    text = replace_once(
        text,
        '        "gh release create",\n    ):\n',
        '        "gh release create",\n'
        '        "environment: release",\n'
        '        "artifact-metadata: write",\n'
        '        "TABDECK_RELEASE_CERT_SHA256",\n'
        '        "git rev-list -n 1",\n'
        '        "keytool -printcert -jarfile",\n'
        '        "--source-commit",\n'
        '        "--signing-cert-sha256",\n'
        '    ):\n',
    )
    text = replace_once(
        text,
        '        if token not in release:\n'
        '            fail(f"Release workflow contract missing: {token}")\n'
        '    ok("Validated CI and signed-release workflow contracts")\n',
        '        if token not in release:\n'
        '            fail(f"Release workflow contract missing: {token}")\n'
        '    packager = (ROOT / "tools/package_release.py").read_text(encoding="utf-8")\n'
        '    for token in (\'"schemaVersion": 2\', \'"sourceCommit"\', \'"releaseTag"\', \'"signingCertificateSha256"\'):\n'
        '        if token not in packager:\n'
        '            fail(f"Release manifest provenance contract missing: {token}")\n'
        '    ok("Validated CI and signed-release workflow contracts")\n',
    )
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    reconstruct_patch()
    apply_patch()
    reconcile_validator()
    for relative in (
        "tools/package_release.py",
        "tools/performance_budget.py",
        "tools/run_core_checks.sh",
        "tools/validate_project.py",
    ):
        (ROOT / relative).chmod(0o755)
    shutil.rmtree(PAYLOAD_DIR)
    run("git", "diff", "--check")
