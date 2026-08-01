#!/usr/bin/env python3
"""Static release validation for TabDeck.

This intentionally uses only Python's standard library plus locally installed
command-line tools. It does not replace an Android/Gradle build, but catches
format, packaging, manifest, extension, and common source-corruption defects.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.parse import urlsplit

from versioning import load_version

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
WARNINGS: list[str] = []
CHECKS: list[str] = []


def fail(message: str) -> None:
    """Record a validation failure for the final project report."""
    ERRORS.append(message)


def warn(message: str) -> None:
    """Record a validation warning for the final project report."""
    WARNINGS.append(message)


def ok(message: str) -> None:
    """Record a successful validation check for the final project report."""
    CHECKS.append(message)


def run(command: list[str], cwd: Path = ROOT) -> subprocess.CompletedProcess[str]:
    """Run a repository command and capture its output without raising automatically."""
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)


def files(patterns: Iterable[str]) -> list[Path]:
    """Collect matching repository files while excluding generated directories."""
    found: set[Path] = set()
    for pattern in patterns:
        found.update(ROOT.rglob(pattern))
    return sorted(p for p in found if p.is_file() and ".git" not in p.parts and "build" not in p.parts)


def validate_xml() -> None:
    """Parse all repository XML files and report malformed documents."""
    xml_files = files(["*.xml"])
    for path in xml_files:
        try:
            ET.parse(path)
        except Exception as exc:  # noqa: BLE001
            fail(f"XML parse failed: {path.relative_to(ROOT)}: {exc}")
    ok(f"Parsed {len(xml_files)} XML files")


def validate_json() -> None:
    """Parse all repository JSON files and report malformed documents."""
    json_files = files(["*.json"])
    for path in json_files:
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            fail(f"JSON parse failed: {path.relative_to(ROOT)}: {exc}")
    ok(f"Parsed {len(json_files)} JSON files")


class StrictHtmlParser(HTMLParser):
    def error(self, message: str) -> None:  # pragma: no cover - required by older Python
        """Raise an HTML parsing error for compatibility with older Python versions."""
        raise ValueError(message)


def validate_html() -> None:
    """Parse all repository HTML files with the strict parser."""
    html_files = files(["*.html"])
    for path in html_files:
        parser = StrictHtmlParser(convert_charrefs=True)
        try:
            parser.feed(path.read_text(encoding="utf-8"))
            parser.close()
        except Exception as exc:  # noqa: BLE001
            fail(f"HTML parse failed: {path.relative_to(ROOT)}: {exc}")
    ok(f"Parsed {len(html_files)} HTML files")


def validate_javascript() -> None:
    """Check JavaScript syntax with Node when it is available."""
    js_files = files(["*.js"])
    node = shutil.which("node")
    if not node:
        warn("node is unavailable; JavaScript syntax was not checked")
        return
    for path in js_files:
        result = run([node, "--check", str(path)])
        if result.returncode != 0:
            fail(f"JavaScript syntax failed: {path.relative_to(ROOT)}\n{result.stderr.strip()}")
    ok(f"Checked {len(js_files)} JavaScript files with node --check")


def validate_shell() -> None:
    """Check shell-script syntax with the system shell when available."""
    scripts = files(["*.sh"])
    shell = shutil.which("sh")
    if not shell:
        warn("sh is unavailable; shell syntax was not checked")
        return
    for path in scripts:
        result = run([shell, "-n", str(path)])
        if result.returncode != 0:
            fail(f"Shell syntax failed: {path.relative_to(ROOT)}\n{result.stderr.strip()}")
    ok(f"Checked {len(scripts)} shell scripts with sh -n")


def validate_embedded_xaml() -> None:
    """Parse XAML documents embedded in PowerShell here-strings."""
    ps_files = files(["*.ps1"])
    xaml_count = 0
    for path in ps_files:
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"(?ms)@(?:\"|\')\s*(<Window\b.*?</Window>)\s*(?:\"|\')@", text):
            xaml_count += 1
            try:
                ET.fromstring(match.group(1))
            except Exception as exc:  # noqa: BLE001
                fail(f"Embedded XAML parse failed: {path.relative_to(ROOT)}: {exc}")
    if not xaml_count:
        warn("No embedded XAML here-string was found")
    else:
        ok(f"Parsed {xaml_count} embedded XAML document(s)")


def strip_kotlin_comments_and_strings(text: str) -> str:
    # Preserve line count while removing lexical regions that make delimiter checks noisy.
    """Remove Kotlin lexical regions while preserving line counts."""
    pattern = re.compile(
        r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])\'',
        flags=re.DOTALL | re.MULTILINE,
    )
    return pattern.sub(lambda m: "\n" * m.group(0).count("\n"), text)


def balanced_delimiters(text: str, pairs: dict[str, str]) -> tuple[bool, str]:
    """Check balanced delimiters and return failure location details."""
    reverse = {v: k for k, v in pairs.items()}
    stack: list[tuple[str, int]] = []
    for index, char in enumerate(text):
        if char in pairs:
            stack.append((char, index))
        elif char in reverse:
            if not stack or stack[-1][0] != reverse[char]:
                return False, f"unexpected {char!r} at offset {index}"
            stack.pop()
    if stack:
        char, index = stack[-1]
        return False, f"unclosed {char!r} at offset {index}"
    return True, ""


def validate_kotlin_structure() -> None:
    """Check Kotlin and Gradle source structure for corruption."""
    kt_files = files(["*.kt", "*.kts"])
    for path in kt_files:
        raw = path.read_text(encoding="utf-8")
        cleaned = strip_kotlin_comments_and_strings(raw)
        balanced, detail = balanced_delimiters(cleaned, {"{": "}", "(": ")", "[": "]"})
        if not balanced:
            fail(f"Kotlin delimiter check failed: {path.relative_to(ROOT)}: {detail}")
        # Detect accidental merge-corruption: identical non-trivial assignment/call lines repeated consecutively.
        previous = ""
        for line_number, line in enumerate(raw.splitlines(), start=1):
            current = line.strip()
            if (
                current == previous
                and len(current) >= 24
                and not current.startswith(("//", "*", "import ", "@"))
                and current not in {"}", ")", "]", "else -> Unit"}
            ):
                fail(
                    f"Suspicious consecutive duplicate line: {path.relative_to(ROOT)}:{line_number}: {current[:100]}"
                )
            previous = current
    ok(f"Checked delimiter integrity for {len(kt_files)} Kotlin/Gradle files")


def validate_powershell_structure() -> None:
    """Parse PowerShell files when a compatible runtime is available."""
    ps_files = files(["*.ps1"])
    pwsh = shutil.which("pwsh") or shutil.which("powershell")
    if pwsh:
        for path in ps_files:
            escaped = str(path).replace("'", "''")
            command = (
                "$errors = $null; "
                f"[void][System.Management.Automation.Language.Parser]::ParseFile('{escaped}', [ref]$null, [ref]$errors); "
                "if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.Message }; exit 1 }"
            )
            result = run([pwsh, "-NoProfile", "-Command", command])
            if result.returncode != 0:
                fail(f"PowerShell parser failed: {path.relative_to(ROOT)}\n{result.stdout}{result.stderr}")
        ok(f"Parsed {len(ps_files)} PowerShell files")
    else:
        warn("PowerShell is unavailable; only embedded XAML and textual invariants were checked")


def validate_manifest_contracts() -> None:
    """Validate Android manifest privacy and browser-discovery contracts."""
    android_manifest = ROOT / "app/src/main/AndroidManifest.xml"
    tree = ET.parse(android_manifest)
    root = tree.getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    package_queries = {
        node.attrib.get(android_ns + "name")
        for node in root.findall("./queries/package")
    }
    expected = {
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.opera.browser",
    }
    missing = sorted(expected - package_queries)
    if missing:
        fail(f"Android manifest is missing browser package queries: {', '.join(missing)}")
    app = root.find("application")
    if app is None:
        fail("Android manifest has no <application>")
    else:
        if app.attrib.get(android_ns + "allowBackup") != "false":
            fail("android:allowBackup must remain false for tab inventory privacy")
        if app.attrib.get(android_ns + "usesCleartextTraffic") != "false":
            fail("android:usesCleartextTraffic must remain false")
    ok("Validated Android manifest privacy and browser-discovery contracts")


def validate_product_version() -> None:
    """Validate the authoritative product version and synchronized consumers."""
    try:
        version = load_version()
    except (OSError, ValueError) as exc:
        fail(f"Invalid version.properties: {exc}")
        return
    result = run([sys.executable, "tools/check_version.py"])
    if result.returncode != 0:
        fail("Public version synchronization failed:\n" + (result.stdout + result.stderr).strip())
    else:
        ok(f"Validated public product version {version.tag} / Android versionCode {version.code}")


def validate_extension_contracts() -> None:
    """Validate extension manifests and synchronized shared runtime files."""
    canonical_runtime = (ROOT / "extensions/shared/bridge-runtime.js").read_text(encoding="utf-8")
    for folder in [ROOT / "extensions/firefox-android", ROOT / "extensions/chromium-desktop"]:
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        generated_runtime = folder / "bridge-runtime.js"
        if not generated_runtime.is_file() or generated_runtime.read_text(encoding="utf-8") != canonical_runtime:
            fail(f"Generated bridge runtime is missing or stale in {folder.relative_to(ROOT)}")
        popup_html = (folder / "popup.html").read_text(encoding="utf-8")
        if popup_html.find('src="bridge-runtime.js"') < 0 or popup_html.find('src="bridge-runtime.js"') > popup_html.find('src="popup.js"'):
            fail(f"Shared bridge runtime must load before popup.js in {folder.relative_to(ROOT)}")
        if manifest.get("manifest_version") not in {2, 3}:
            fail(f"Unsupported extension manifest version in {folder.relative_to(ROOT)}")
        popup = manifest.get("browser_action", manifest.get("action", {})).get("default_popup")
        if popup and not (folder / popup).exists():
            fail(f"Missing extension popup {popup} in {folder.relative_to(ROOT)}")
        for icon_path in manifest.get("icons", {}).values():
            if not (folder / icon_path).exists():
                fail(f"Missing extension icon {icon_path} in {folder.relative_to(ROOT)}")
    ok("Validated extension manifests and synchronized bridge runtime")


def validate_gradle_wrapper_files() -> None:
    """Validate the committed Gradle wrapper artifacts."""
    wrapper_jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    if not wrapper_jar.is_file() or wrapper_jar.stat().st_size < 10_000:
        fail("Gradle wrapper JAR is missing or implausibly small")
    else:
        ok(f"Validated committed Gradle wrapper JAR ({wrapper_jar.stat().st_size} bytes)")


def validate_build_coordinates() -> None:
    """Validate pinned build-tool and dependency coordinates."""
    root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    required = {
        "AGP 9.3.1": 'com.android.application") version "9.3.1"',
        "Kotlin 2.4.10": 'org.jetbrains.kotlin.plugin.compose") version "2.4.10"',
        "KSP 2.3.10": 'com.google.devtools.ksp") version "2.3.10"',
        "Compose BOM 2026.06.01": 'compose-bom:2026.06.01',
        "Room 2.8.4": 'val roomVersion = "2.8.4"',
        "Room Paging 2.8.4": 'androidx.room:room-paging:$roomVersion',
        "WorkManager 2.11.2": 'work-runtime-ktx:2.11.2',
        "RE2/J 1.8": 're2j:1.8',
        "compileSdk 36": 'compileSdk = 36',
        "targetSdk 36": 'targetSdk = 36',
        "Gradle 9.6.1": 'gradle-9.6.1-bin.zip',
    }
    combined = root_build + "\n" + app_build + "\n" + wrapper
    for label, needle in required.items():
        if needle not in combined:
            fail(f"Expected build coordinate missing: {label}")
    ok("Validated pinned build coordinates")


def validate_text_integrity() -> None:
    """Validate UTF-8 encoding and reject unexpected control bytes."""
    candidates = files(["*.kt", "*.kts", "*.js", "*.json", "*.xml", "*.html", "*.ps1", "*.sh", "*.md", "*.yml", "*.yaml", "*.properties"])
    for path in candidates:
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            fail(f"Invalid UTF-8 in {path.relative_to(ROOT)}: {exc}")
            continue
        if "\x00" in text:
            fail(f"NUL byte found in {path.relative_to(ROOT)}")
        for index, char in enumerate(text):
            code = ord(char)
            if code < 32 and char not in {"\n", "\r", "\t"}:
                fail(f"Unexpected control character U+{code:04X} in {path.relative_to(ROOT)} at offset {index}")
                break
    ok(f"Validated UTF-8 and control-byte integrity for {len(candidates)} text files")


def validate_compose_icon_imports() -> None:
    """Validate that used Compose icons have explicit imports."""
    checked = 0
    for path in files(["*.kt"]):
        text = path.read_text(encoding="utf-8")
        used = set(re.findall(r"Icons\.Outlined\.([A-Za-z0-9_]+)", text))
        if not used:
            continue
        checked += 1
        imported = set(re.findall(r"import androidx\.compose\.material\.icons\.outlined\.([A-Za-z0-9_]+)", text))
        missing = sorted(used - imported)
        if missing:
            fail(f"Missing outlined icon imports in {path.relative_to(ROOT)}: {', '.join(missing)}")
    ok(f"Validated Compose icon imports in {checked} Kotlin files")


def validate_release_contracts() -> None:
    """Validate cross-component runtime and release invariants."""
    bridge = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/LocalBridgeService.kt").read_text(encoding="utf-8")
    backup = (ROOT / "app/src/main/java/com/tabdeck/app/data/SnapshotJsonCodec.kt").read_text(encoding="utf-8")
    database = (ROOT / "app/src/main/java/com/tabdeck/app/data/local/TabDeckDatabase.kt").read_text(encoding="utf-8")
    query_codec = (ROOT / "app/src/main/java/com/tabdeck/app/data/local/LibraryQueryCodec.kt").read_text(encoding="utf-8")
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    screens = (ROOT / "app/src/main/java/com/tabdeck/app/ui/Screens.kt").read_text(encoding="utf-8")
    repository = (ROOT / "app/src/main/java/com/tabdeck/app/data/TabDeckRepository.kt").read_text(encoding="utf-8")
    view_model = (ROOT / "app/src/main/java/com/tabdeck/app/TabDeckViewModel.kt").read_text(encoding="utf-8")
    export_codec = (ROOT / "app/src/main/java/com/tabdeck/app/data/TabExportCodec.kt").read_text(encoding="utf-8")
    bridge_network = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgeNetwork.kt").read_text(encoding="utf-8")
    bridge_parser = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8")
    endpoint_match = re.search(r'const val LOOPBACK_ENDPOINT\s*=\s*"([^"]+)"', bridge_network)
    endpoint_host = urlsplit(endpoint_match.group(1)).hostname if endpoint_match else None
    checks = {
        "bridge API v3": 'request.path == "/api/v3/import"' in bridge and '.put("version", 3)' in bridge,
        "bridge compatibility": all(f'/api/v{version}/import' in bridge for version in (1, 2, 3)),
        "backup v3": "const val VERSION = 3" in backup,
        "Room schema v3": "version = 3" in database and "MIGRATION_2_3" in database,
        "smart-view query v2": '.put("version", 2)' in query_codec,
        "source-aware filters": "sourceDevices" in query_codec and "sourceGroups" in query_codec and "Captured sources" in screens,
        "query-wide bulk controls": "selectAllMatching" in screens and "editTagsOnSelected" in screens,
        "chunked tag editing": "suspend fun editTags" in repository and "chunked(SQLITE_IN_CHUNK)" in repository,
        "quick control widget": "QuickCaptureWidgetReceiver" in manifest and "quick_capture_widget_info" in manifest,
        "automation and recovery widgets": all(token in manifest for token in (
            "TransferStatusWidgetReceiver", "DeckLauncherWidgetReceiver",
            "transfer_status_widget_info", "deck_launcher_widget_info",
        )),
        "automatic maintenance worker": (ROOT / "app/src/main/java/com/tabdeck/app/MaintenanceWorker.kt").is_file(),
        "maintenance policy core coverage": "MaintenancePolicy.kt" in (ROOT / "tools/run_core_checks.sh").read_text(encoding="utf-8"),
        "deck widget deep link": "ACTION_OPEN_DECK" in view_model and "EXTRA_DECK_ID" in view_model,
        "core harness Compose stub": "ComposeRuntimeStubs.kt" in (ROOT / "tools/run_core_checks.sh").read_text(encoding="utf-8"),
        "human-readable exports": all(token in export_codec for token in ("MARKDOWN", "CSV", "NETSCAPE_BOOKMARKS", "csvCell")),
        "spreadsheet export hardening": "trimStart().firstOrNull() in setOf" in export_codec,
        "UTF-8 bounded share import": "fun utf8Prefix" in view_model and "MAX_IMPORT_DOCUMENT_BYTES" in view_model,
        "typed backup classification": "sealed interface DecodeResult" in backup and "decodeClassified" in view_model,
        "loopback-only bridge": endpoint_host in {"127.0.0.1", "localhost", "::1"} and "0.0.0.0" not in bridge,
        "session-scoped source identity": "SourceIdentity.encodeTabId" in bridge_parser,
        "identity-version reconciliation guard": "identityVersion == CURRENT_IDENTITY_VERSION" in bridge_parser,
    }
    for label, passed in checks.items():
        if not passed:
            fail(f"Release contract missing: {label}")
    product_version = load_version()
    for folder in [ROOT / "extensions/firefox-android", ROOT / "extensions/chromium-desktop"]:
        extension_manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        if extension_manifest.get("version") != product_version.name:
            fail(
                f"Extension version is {extension_manifest.get('version')!r} in "
                f"{folder.relative_to(ROOT)}; expected {product_version.name!r}"
            )
        popup = (folder / "popup.js").read_text(encoding="utf-8")
        if "/api/v3/import" not in popup or "/api/v2/import" not in popup:
            fail(f"Extension endpoint compatibility is incomplete in {folder.relative_to(ROOT)}")
        if "testBridgeConnection" not in popup or "'/health'" not in popup:
            fail(f"Extension bridge preflight is missing in {folder.relative_to(ROOT)}")
        if "sourceSessionId" not in popup or "getSourceSession" not in popup:
            fail(f"Extension session-scoped tab identity is missing in {folder.relative_to(ROOT)}")
    parser = (ROOT / "app/src/main/java/com/tabdeck/app/bridge/BridgePayloadParser.kt").read_text(encoding="utf-8")
    identity = (ROOT / "app/src/main/java/com/tabdeck/app/engine/SourceIdentity.kt").read_text(encoding="utf-8")
    if "SourceIdentity.encodeTabId" not in parser or "sourceSessionId" not in parser:
        fail("Bridge parser is missing session-scoped source identity")
    if "sid1:" not in identity or "isSessionScoped" not in identity:
        fail("Source identity codec contract is incomplete")
    for required_file in (
        ROOT / "tools/performance_budget.py",
        ROOT / "tools/performance-budgets.json",
        ROOT / "desktop-link/Test-TabDeckLink.ps1",
        ROOT / "docs/adr/0001-durable-source-identity.md",
        ROOT / "docs/adr/0002-loopback-bridge-trust-boundary.md",
        ROOT / "docs/adr/0003-release-provenance.md",
    ):
        if not required_file.is_file():
            fail(f"Missing verification contract: {required_file.relative_to(ROOT)}")
    ok("Validated TabDeck v1 product, compatibility, bridge, paging, export, bulk-control, widget, and source-identity contracts")


def validate_workflow_contracts() -> None:
    """Validate CI and release workflow safety contracts."""
    workflows = {
        "CI": ROOT / ".github/workflows/ci.yml",
        "Release": ROOT / ".github/workflows/release.yml",
    }
    for label, path in workflows.items():
        if not path.is_file():
            fail(f"Missing {label} workflow: {path.relative_to(ROOT)}")
            continue
        text = path.read_text(encoding="utf-8")
        if "\t" in text:
            fail(f"Tab character found in workflow YAML: {path.relative_to(ROOT)}")
        required_tokens = ["actions/checkout@v6", "actions/setup-java@v5", "gradle/actions/setup-gradle@v6"]
        for token in required_tokens:
            if token not in text:
                fail(f"{label} workflow is missing {token}")
    ci = workflows["CI"].read_text(encoding="utf-8") if workflows["CI"].is_file() else ""
    release = workflows["Release"].read_text(encoding="utf-8") if workflows["Release"].is_file() else ""
    if "gradle wrapper --gradle-version" in ci or "gradle wrapper --gradle-version" in release:
        fail("Workflows must execute the committed Gradle wrapper instead of regenerating it")
    for token in ("Verify committed Gradle wrapper", "distributionSha256Sum", "./gradlew --version"):
        if token not in ci:
            fail(f"CI workflow contract missing: {token}")
    for token in (
        "tools/check_version.py --tag",
        "lintRelease",
        "assembleRelease",
        "bundleRelease",
        "apksigner",
        "jarsigner -verify -strict",
        "--require-android-artifacts",
        "actions/upload-artifact@v7",
        "actions/attest@v4",
        "gh release create",
        "environment: release",
        "artifact-metadata: write",
        "TABDECK_RELEASE_CERT_SHA256",
        "git rev-list -n 1",
        "keytool -printcert -jarfile",
        "--source-commit",
        "--signing-cert-sha256",
    ):
        if token not in release:
            fail(f"Release workflow contract missing: {token}")
    packager = (ROOT / "tools/package_release.py").read_text(encoding="utf-8")
    for token in ('"schemaVersion": 2', '"sourceCommit"', '"releaseTag"', '"signingCertificateSha256"'):
        if token not in packager:
            fail(f"Release manifest provenance contract missing: {token}")
    ok("Validated CI and signed-release workflow contracts")


def validate_no_obvious_secrets() -> None:
    """Scan source and documentation for obvious embedded secrets."""
    candidates = files(["*.kt", "*.kts", "*.js", "*.json", "*.ps1", "*.md", "*.xml"])
    patterns = {
        "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        "GitHub token": re.compile(r"gh[pousr]_[A-Za-z0-9_]{30,}"),
        "OpenAI key": re.compile(r"sk-(?:proj-)?[A-Za-z0-9_-]{24,}"),
    }
    for path in candidates:
        text = path.read_text(encoding="utf-8", errors="replace")
        for label, pattern in patterns.items():
            if pattern.search(text):
                fail(f"Potential {label} found in {path.relative_to(ROOT)}")
    ok("Scanned source and documentation for obvious embedded secrets")


def main() -> int:
    """Run the command-line entry point and return its exit status."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, help="Write the validation report to this path")
    args = parser.parse_args()

    validate_xml()
    validate_json()
    validate_html()
    validate_javascript()
    validate_shell()
    validate_embedded_xaml()
    validate_text_integrity()
    validate_kotlin_structure()
    validate_compose_icon_imports()
    validate_powershell_structure()
    validate_manifest_contracts()
    validate_product_version()
    validate_extension_contracts()
    validate_gradle_wrapper_files()
    validate_build_coordinates()
    validate_release_contracts()
    validate_workflow_contracts()
    validate_no_obvious_secrets()

    lines = ["TabDeck static validation", "=" * 25]
    lines.extend(f"PASS: {item}" for item in CHECKS)
    lines.extend(f"WARN: {item}" for item in WARNINGS)
    lines.extend(f"FAIL: {item}" for item in ERRORS)
    lines.append("")
    lines.append(f"Result: {'FAIL' if ERRORS else 'PASS'} ({len(CHECKS)} checks, {len(WARNINGS)} warnings, {len(ERRORS)} errors)")
    report = "\n".join(lines) + "\n"
    print(report, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
    return 1 if ERRORS else 0


if __name__ == "__main__":
    sys.exit(main())
