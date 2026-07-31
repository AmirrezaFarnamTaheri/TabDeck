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
    ERRORS.append(message)


def warn(message: str) -> None:
    WARNINGS.append(message)


def ok(message: str) -> None:
    CHECKS.append(message)


def run(command: list[str], cwd: Path = ROOT) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)


def files(patterns: Iterable[str]) -> list[Path]:
    found: set[Path] = set()
    for pattern in patterns:
        found.update(ROOT.rglob(pattern))
    return sorted(p for p in found if p.is_file() and ".git" not in p.parts and "build" not in p.parts)


def validate_xml() -> None:
    xml_files = files(["*.xml"])
    for path in xml_files:
        try:
            ET.parse(path)
        except Exception as exc:  # noqa: BLE001
            fail(f"XML parse failed: {path.relative_to(ROOT)}: {exc}")
    ok(f"Parsed {len(xml_files)} XML files")


def validate_json() -> None:
    json_files = files(["*.json"])
    for path in json_files:
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            fail(f"JSON parse failed: {path.relative_to(ROOT)}: {exc}")
    ok(f"Parsed {len(json_files)} JSON files")


class StrictHtmlParser(HTMLParser):
    def error(self, message: str) -> None:  # pragma: no cover - required by older Python
        raise ValueError(message)


def validate_html() -> None:
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
    pattern = re.compile(
        r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])\'',
        flags=re.DOTALL | re.MULTILINE,
    )
    return pattern.sub(lambda m: "\n" * m.group(0).count("\n"), text)


def balanced_delimiters(text: str, pairs: dict[str, str]) -> tuple[bool, str]:
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
    wrapper_jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    if not wrapper_jar.is_file() or wrapper_jar.stat().st_size < 10_000:
        fail("Gradle wrapper JAR is missing or implausibly small")
    else:
        ok(f"Validated committed Gradle wrapper JAR ({wrapper_jar.stat().st_size} bytes)")


def validate_build_coordinates() -> None:
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
        "source-aware filters": "sourceDevices" in query_codec and "sourceGroups" in query_codec and "Source topology" in screens,
        "query-wide bulk controls": "selectAllMatching" in screens and "editTagsOnSelected" in screens,
        "chunked tag editing": "suspend fun editTags" in repository and "chunked(SQLITE_IN_CHUNK)" in repository,
        "quick control widget": "QuickCaptureWidgetReceiver" in manifest and "quick_capture_widget_info" in manifest,
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
            fail(f"Extension session-scoped tab identity is missing in {folder.relative_to(RõB—Ò"¢'6W"Ò…$ôõBò&÷7&2öÖ–âö¦fö6öÒ÷F&FV6²öö'&–FvRô'&–FvU–ÆöE'6W"æ·B"’ç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"¢–FVçF—G’Ò…$ôõBò&÷7&2öÖ–âö¦fö6öÒ÷F&FV6²ööVæv–æRõ6÷W&6T–FVçF—G’æ·B"’ç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"¢–b%6÷W&6T–FVçF—G’æVæ6öFUF$–B"æ÷B–â'6W"÷"'6÷W&6U6W76–öä–B"æ÷B–â'6W# ¢f–Â‚$'&–FvR'6W"—2Ö—76–ær6W76–öâ×66÷VB6÷W&6R–FVçF—G’"¢–b'6–C¢"æ÷B–â–FVçF—G’÷"&—56W76–öå66÷VB"æ÷B–â–FVçF—G“ ¢f–Â‚%6÷W&6R–FVçF—G’6öFV26öçG&7B—2–æ6ö×ÆWFR"¢f÷"&WV—&VEöf–ÆR–â€¢$ôõBò'FööÇ2÷W&f÷&Öæ6Uö'VFvWBç’"À¢$ôõBò'FööÇ2÷W&f÷&Öæ6RÖ'VFvWG2æ§6öâ"À¢$ôõBò&FW6·F÷ÖÆ–æ²õFW7BÕF$FV6´Æ–æ²ç3"À¢$ôõBò&Fö72öG"óÖGW&&ÆR×6÷W&6RÖ–FVçF—G’æÖB"À¢$ôõBò&Fö72öG"ó"ÖÆö÷&6²Ö'&–FvR×G'W7BÖ&÷VæF'’æÖB"À¢$ôõBò&Fö72öG"ó2×&VÆV6R×&÷fVææ6RæÖB"À¢“ ¢–bæ÷B&WV—&VEöf–ÆRæ—5öf–ÆR‚“ ¢f–Â†b$Ö—76–ærfW&–f–6F–öâ6öçG&7C¢·&WV—&VEöf–ÆRç&VÆF—fU÷Fò…$ôõB—Ò"¢ö²‚%fÆ–FFVBF$FV6²c&öGV7BÂ6ö×F–&–Æ—G’Â'&–FvRÂv–ærÂW‡÷'BÂ'VÆ²Ö6öçG&öÂÂv–FvWBÂæB6÷W&6RÖ–FVçF—G’6öçG&7G2"  ¦FVbfÆ–FFU÷v÷&¶fÆ÷uö6öçG&7G2‚’ÓâæöæS ¢v÷&¶fÆ÷w2Ò°¢$4’#¢$ôõBò"æv—F‡V"÷v÷&¶fÆ÷w2ö6’ç–ÖÂ"À¢%&VÆV6R#¢$ôõBò"æv—F‡V"÷v÷&¶fÆ÷w2÷&VÆV6Rç–ÖÂ"À¢Ğ¢f÷"Æ&VÂÂF‚–âv÷&¶fÆ÷w2æ—FV×2‚“ ¢–bæ÷BF‚æ—5öf–ÆR‚“ ¢f–Â†b$Ö—76–ær¶Æ&VÇÒv÷&¶fÆ÷s¢·F‚ç&VÆF—fU÷Fò…$ôõB—Ò"¢6öçF–çVP¢FW‡BÒF‚ç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"¢–b%ÇB"–âFW‡C ¢f–Â†b%F"6†&7FW"f÷VæB–âv÷&¶fÆ÷r”ÔÃ¢·F‚ç&VÆF—fU÷Fò…$ôõB—Ò"¢&WV—&VE÷Fö¶Vç2Ò²&7F–öç2ö6†V6¶÷WDcb"Â&7F–öç2÷6WGWÖ¦fcR"Â&w&FÆRö7F–öç2÷6WGWÖw&FÆTcb%Ğ¢f÷"Fö¶Vâ–â&WV—&VE÷Fö¶Vç3 ¢–bFö¶Vâæ÷B–âFW‡C ¢f–Â†b'¶Æ&VÇÒv÷&¶fÆ÷r—2Ö—76–ær·Fö¶VçÒ"¢6’Òv÷&¶fÆ÷w5²$4’%Òç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"’–bv÷&¶fÆ÷w5²$4’%Òæ—5öf–ÆR‚’VÇ6R" ¢&VÆV6RÒv÷&¶fÆ÷w5²%&VÆV6R%Òç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"’–bv÷&¶fÆ÷w5²%&VÆV6R%Òæ—5öf–ÆR‚’VÇ6R" ¢–b&w&FÆRw&W"ÒÖw&FÆR×fW'6–öâ"–â6’÷"&w&FÆRw&W"ÒÖw&FÆR×fW'6–öâ"–â&VÆV6S ¢f–Â‚%v÷&¶fÆ÷w2×W7BW†V7WFRF†R6öÖÖ—GFVBw&FÆRw&W"–ç7FVBöb&VvVæW&F–ær—B"¢f÷"Fö¶Vâ–â‚%fW&–g’6öÖÖ—GFVBw&FÆRw&W""Â&F—7G&–'WF–öå6†#Se7VÒ"Â"âöw&FÆWrÒ×fW'6–öâ"“ ¢–bFö¶Vâæ÷B–â6“ ¢f–Â†b$4’v÷&¶fÆ÷r6öçG&7BÖ—76–æs¢·Fö¶VçÒ"¢f÷"Fö¶Vâ–â€¢'FööÇ2ö6†V6µ÷fW'6–öâç’Ò×Fr"À¢&Æ–çE&VÆV6R"À¢&76VÖ&ÆU&VÆV6R"À¢&'VæFÆU&VÆV6R"À¢&·6–væW""À¢&¦'6–væW"×fW&–g’×7G&–7B"À¢"Ò×&WV—&RÖæG&ö–BÖ'F–f7G2"À¢&7F–öç2÷WÆöBÖ'F–f7Dcr"À¢&7F–öç2öGFW7DcB"À¢&v‚&VÆV6R7&VFR"À¢&Vçf—&öæÖVçC¢&VÆV6R"À¢&'F–f7BÖÖWFFF¢w&—FR"À¢%D$DT4µõ$TÄT4Uô4U%Eõ4„#Sb"À¢&v—B&WbÖÆ—7BÖâ"À¢&¶W—FööÂ×&–çF6W'BÖ¦&f–ÆR"À¢"Ò×6÷W&6RÖ6öÖÖ—B"À¢"Ò×6–væ–ærÖ6W'B×6†#Sb"À¢“ ¢–bFö¶Vâæ÷B–â&VÆV6S ¢f–Â†b%&VÆV6Rv÷&¶fÆ÷r6öçG&7BÖ—76–æs¢·Fö¶VçÒ"¢6¶vW"Ò…$ôõBò'FööÇ2÷6¶vU÷&VÆV6Rç’"’ç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"¢f÷"Fö¶Vâ–â‚r'66†VÖfW'6–öâ#¢"rÂr'6÷W&6T6öÖÖ—B"rÂr'&VÆV6UFr"rÂr'6–væ–æt6W'F–f–6FU6†#Sb"r“ ¢–bFö¶Vâæ÷B–â6¶vW# ¢f–Â†b%&VÆV6RÖæ–fW7B&÷fVææ6R6öçG&7BÖ—76–æs¢·Fö¶VçÒ"¢ö²‚%fÆ–FFVB4’æB6–væVB×&VÆV6Rv÷&¶fÆ÷r6öçG&7G2"  ¦FVbfÆ–FFUöæõöö'f–÷W5÷6V7&WG2‚’ÓâæöæS ¢6æF–FFW2Òf–ÆW2…²"¢æ·B"Â"¢æ·G2"Â"¢æ§2"Â"¢æ§6öâ"Â"¢ç3"Â"¢æÖB"Â"¢ç†ÖÂ%Ò¢GFW&ç2Ò°¢'&—fFR¶W’#¢&Ræ6ö×–ÆR‡""ÒÒÒÒÔ$Tt”âƒó¥%4ÄT2ÄõTå54‚“õ$•dDR´U’ÒÒÒÒÒ"’À¢$v—D‡V"Fö¶Vâ#¢&Ræ6ö×–ÆR‡"&v…·÷W7%Õõ´Õ¦×£Ó•õ×³3ÇÒ"’À¢$÷Vä’¶W’#¢&Ræ6ö×–ÆR‡"'6²Òƒó§&ö¢Ò“õ´Õ¦×£Ó•òÕ×³#BÇÒ"’À¢Ğ¢f÷"F‚–â6æF–FFW3 ¢FW‡BÒF‚ç&VE÷FW‡B†Væ6öF–æsÒ'WFbÓ‚"ÂW'&÷'3Ò'&WÆ6R"¢f÷"Æ&VÂÂGFW&â–âGFW&ç2æ—FV×2‚“ ¢–bGFW&âç6V&6‚‡FW‡B“ ¢f–Â†b%÷FVçF–Â¶Æ&VÇÒf÷VæB–â·F‚ç&VÆF—fU÷Fò…$ôõB—Ò"¢ö²‚%66ææVB6÷W&6RæBFö7VÖVçFF–öâf÷"ö'f–÷W2VÖ&VFFVB6V7&WG2"  ¦FVbÖ–â‚’Óâ–çC ¢'6W"Ò&w'6Rä&wVÖVçE'6W"‚¢'6W"æFEö&wVÖVçB‚"Ò×&W÷'B"ÂG—SÕF‚Â†VÇÒ%w&—FRF†RfÆ–FF–öâ&W÷'BFòF†—2F‚"¢&w2Ò'6W"ç'6Uö&w2‚ ¢fÆ–FFU÷†ÖÂ‚¢fÆ–FFUö§6öâ‚¢fÆ–FFUö‡FÖÂ‚¢fÆ–FFUö¦f67&—B‚¢fÆ–FFU÷6†VÆÂ‚¢fÆ–FFUöVÖ&VFFVE÷†ÖÂ‚¢fÆ–FFU÷FW‡Eö–çFVw&—G’‚¢fÆ–FFUö¶÷FÆ–å÷7G'V7GW&R‚¢fÆ–FFUö6ö×÷6Uö–6öåö–×÷'G2‚¢fÆ–FFU÷÷vW'6†VÆÅ÷7G'V7GW&R‚¢fÆ–FFUöÖæ–fW7Eö6öçG&7G2‚¢fÆ–FFU÷&öGV7E÷fW'6–öâ‚¢fÆ–FFUöW‡FVç6–öåö6öçG&7G2‚¢fÆ–FFUöw&FÆU÷w&W%öf–ÆW2‚¢fÆ–FFUö'V–ÆEö6ö÷&F–æFW2‚¢fÆ–FFU÷&VÆV6Uö6öçG&7G2‚¢fÆ–FFU÷v÷&¶fÆ÷uö6öçG&7G2‚¢fÆ–FFUöæõöö'f–÷W5÷6V7&WG2‚ ¢Æ–æW2Ò²%F$FV6²7FF–2fÆ–FF–öâ"Â#Ò"¢#UĞ¢Æ–æW2æW‡FVæB†b%53¢¶—FV×Ò"f÷"—FVÒ–â4„T4µ2¢Æ–æW2æW‡FVæB†b%t$ã¢¶—FV×Ò"f÷"—FVÒ–ât$ä”äu2¢Æ–æW2æW‡FVæB†b$d”Ã¢¶—FV×Ò"f÷"—FVÒ–âU%$õ%2¢Æ–æW2æVæB‚""¢Æ–æW2æVæB†b%&W7VÇC¢²td”Âr–bU%$õ%2VÇ6Ru52wÒ‡¶ÆVâ„4„T4µ2—Ò6†V6·2Â¶ÆVâ…t$ä”äu2—Òv&æ–æw2Â¶ÆVâ„U%$õ%2—ÒW'&÷'2’"¢&W÷'BÒ%Æâ"æ¦ö–â†Æ–æW2’²%Æâ ¢&–çB‡&W÷'BÂVæCÒ""¢–b&w2ç&W÷'C ¢&w2ç&W÷'Bç&VçBæÖ¶F—"‡&VçG3ÕG'VRÂW†—7Eöö³ÕG'VR¢&w2ç&W÷'Bçw&—FU÷FW‡B‡&W÷'BÂVæ6öF–æsÒ'WFbÓ‚"¢&WGW&â–bU%$õ%2VÇ6R   ¦–bõöæÖUõòÓÒ%õöÖ–åõò# ¢7—2æW†—B†Ö–â‚’ 