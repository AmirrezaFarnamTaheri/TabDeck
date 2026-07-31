#!/usr/bin/env python3
"""Build deterministic TabDeck release archives and verify their integrity."""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import zipfile

from versioning import ROOT, ProductVersion, load_version

EXCLUDED_PARTS = {".git", ".gradle", ".idea", "build", "dist", "__pycache__"}
EXCLUDED_NAMES = {"local.properties"}
EXCLUDED_SUFFIXES = {".iml", ".jks", ".keystore", ".key", ".p12", ".pfx", ".pem", ".pyc", ".pyo"}


def run_checked(command: list[str], cwd: Path = ROOT) -> str:
    """Run a subprocess and raise an actionable error when it fails."""
    process = subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)
    output = process.stdout + process.stderr
    if process.returncode != 0:
        raise RuntimeError(f"Command failed ({process.returncode}): {' '.join(command)}\n{output}")
    return output


def fixed_time(version: ProductVersion) -> tuple[int, int, int, int, int, int]:
    """Return the deterministic archive timestamp used for release packaging."""
    year, month, day = (int(part) for part in version.release_date.split("-"))
    return max(1980, year), month, day, 12, 0, 0


def should_include(path: Path) -> bool:
    """Return whether a repository path belongs in a source archive."""
    relative = path.relative_to(ROOT)
    if any(part in EXCLUDED_PARTS for part in relative.parts):
        return False
    if path.name in EXCLUDED_NAMES or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    return path.is_file() and not path.is_symlink()


def zip_info(archive_name: str, executable: bool, timestamp: tuple[int, int, int, int, int, int]) -> zipfile.ZipInfo:
    """Build deterministic ZIP metadata for an archive entry."""
    info = zipfile.ZipInfo(archive_name, timestamp)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (stat.S_IFREG | (0o755 if executable else 0o644)) << 16
    info.create_system = 3
    return info


def is_executable(path: Path) -> bool:
    """Return whether a path should retain executable permissions in archives."""
    return bool(path.stat().st_mode & stat.S_IXUSR) or path.name == "gradlew" or path.suffix == ".sh"


def write_archive(
    destination: Path,
    entries: list[tuple[Path, str]],
    timestamp: tuple[int, int, int, int, int, int],
) -> None:
    """Write a deterministic ZIP archive from the supplied entries."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for source, archive_name in sorted(entries, key=lambda item: item[1]):
            archive.writestr(zip_info(archive_name, is_executable(source), timestamp), source.read_bytes())
    with zipfile.ZipFile(destination, "r") as archive:
        bad = archive.testzip()
        if bad:
            raise RuntimeError(f"Archive integrity failure in {destination.name}: {bad}")
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError(f"Duplicate archive entries in {destination.name}")


def source_entries(prefix: str) -> list[tuple[Path, str]]:
    """Collect deterministic source-archive entries from the repository."""
    return [
        (path, f"{prefix}/{path.relative_to(ROOT).as_posix()}")
        for path in ROOT.rglob("*")
        if should_include(path)
    ]


def folder_entries(folder: Path, prefix: str = "") -> list[tuple[Path, str]]:
    """Collect deterministic archive entries from a repository folder."""
    entries: list[tuple[Path, str]] = []
    for path in folder.rglob("*"):
        if path.is_file() and not path.is_symlink() and "__pycache__" not in path.parts:
            entries.append((path, f"{prefix}{path.relative_to(folder).as_posix()}"))
    return entries


def sha256(path: Path) -> str:
    """Return the SHA-256 digest of a file."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def media_type(path: Path) -> str:
    """Return the release media type for an artifact path."""
    overrides = {
        ".apk": "application/vnd.android.package-archive",
        ".xpi": "application/x-xpinstall",
    }
    return overrides.get(path.suffix.lower(), mimetypes.guess_type(path.name)[0] or "application/octet-stream")


def copy_android_apk(output: Path, prefix: str, required: bool) -> list[Path]:
    """Copy the verified community-signed Android APK into the release directory."""
    source = ROOT / "app/build/outputs/apk/release/app-release.apk"
    if not source.is_file():
        if required:
            raise RuntimeError(f"Required Android release APK is missing: {source}")
        return []
    destination = output / f"{prefix}.apk"
    shutil.copyfile(source, destination)
    copied = [destination]
    mapping = ROOT / "app/build/outputs/mapping/release/mapping.txt"
    if mapping.is_file():
        mapping_destination = output / f"{prefix}-mapping.txt"
        shutil.copyfile(mapping, mapping_destination)
        copied.append(mapping_destination)
    return copied


def verify_archive_entries(required: dict[Path, list[str]]) -> None:
    """Verify that an archive contains the required release entries."""
    for archive_path, expected in required.items():
        with zipfile.ZipFile(archive_path) as archive:
            missing = [entry for entry in expected if entry not in archive.namelist()]
            if missing:
                raise RuntimeError(f"{archive_path.name} missing required entries: {missing}")


def main() -> int:
    """Run the command-line entry point and return its exit status."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=ROOT / "dist")
    parser.add_argument("--require-apk", action="store_true", help="Fail unless the community release APK exists")
    parser.add_argument("--require-android-artifacts", dest="require_apk", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--source-commit", default="", help="Immutable Git commit used for this artifact set")
    parser.add_argument("--release-tag", default="", help="Release tag bound to the source commit")
    parser.add_argument("--signing-cert-sha256", default="", help="Community APK certificate fingerprint")
    args = parser.parse_args()

    version = load_version()
    source_commit = args.source_commit.strip().lower()
    release_tag = args.release_tag.strip() or version.tag
    signing_cert = args.signing_cert_sha256.replace(":", "").replace(" ", "").upper()
    if source_commit and (len(source_commit) != 40 or any(char not in "0123456789abcdef" for char in source_commit)):
        raise RuntimeError("--source-commit must be a 40-character lowercase Git SHA")
    if release_tag != version.tag:
        raise RuntimeError(f"Release tag {release_tag!r} does not match version tag {version.tag!r}")
    if signing_cert and (len(signing_cert) != 64 or any(char not in "0123456789ABCDEF" for char in signing_cert)):
        raise RuntimeError("--signing-cert-sha256 must be a 64-character SHA-256 fingerprint")
    if args.require_apk and (not source_commit or not signing_cert):
        raise RuntimeError("Installable releases require --source-commit and --signing-cert-sha256")

    prefix = version.artifact_prefix
    timestamp = fixed_time(version)
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    for stale in output.glob(f"{prefix}*"):
        if stale.is_file():
            stale.unlink()

    run_checked([sys.executable, "tools/check_version.py"])
    validation_path = output / f"{prefix}-validation-report.txt"
    validator_output = run_checked([sys.executable, "tools/validate_project.py", "--report", str(validation_path)])
    core_output = run_checked(["bash", "tools/run_core_checks.sh"])

    source_zip = output / f"{prefix}-source.zip"
    firefox_xpi = output / f"{prefix}-Firefox-Bridge-unsigned.xpi"
    chromium_zip = output / f"{prefix}-Chromium-Bridge.zip"
    desktop_zip = output / f"{prefix}-Desktop-Link.zip"
    branding_zip = output / f"{prefix}-Branding.zip"

    write_archive(source_zip, source_entries(prefix), timestamp)
    write_archive(firefox_xpi, folder_entries(ROOT / "extensions/firefox-android"), timestamp)
    write_archive(chromium_zip, folder_entries(ROOT / "extensions/chromium-desktop"), timestamp)
    desktop_root = f"TabDeck-Desktop-Link-v{version.name}/"
    write_archive(desktop_zip, folder_entries(ROOT / "desktop-link", prefix=desktop_root), timestamp)
    write_archive(branding_zip, folder_entries(ROOT / "branding"), timestamp)

    required = {
        source_zip: [
            f"{prefix}/README.md",
            f"{prefix}/version.properties",
            f"{prefix}/app/build.gradle.kts",
            f"{prefix}/.github/workflows/release.yml",
            f"{prefix}/branding/TabDeck-mark.svg",
            f"{prefix}/release/tabdeck-community.jks.base64",
        ],
        firefox_xpi: ["manifest.json", "popup.html", "popup.js", "tabdeck-mark.svg"],
        chromium_zip: ["manifest.json", "popup.html", "popup.js", "tabdeck-mark.svg"],
        desktop_zip: [
            f"{desktop_root}TabDeckLink.ps1",
            f"{desktop_root}README.md",
            f"{desktop_root}TabDeck-mark.svg",
        ],
        branding_zip: ["TabDeck-mark.svg", "TabDeck-lockup.svg", "README.md"],
    }
    verify_archive_entries(required)
    android_artifacts = copy_android_apk(output, prefix, args.require_apk)

    with validation_path.open("a", encoding="utf-8") as report:
        report.write("\nExecutable core harness\n=======================\n")
        report.write(core_output.strip() + "\n")
        report.write("\nPackaging verification\n======================\n")
        for archive_path in required:
            with zipfile.ZipFile(archive_path) as archive:
                report.write(f"PASS: {archive_path.name}: {len(archive.namelist())} unique entries; CRC test passed\n")
        if android_artifacts:
            report.write("PASS: Community-signed Android release APK was copied into the release set.\n")
        else:
            report.write("INFO: No Android APK was packaged; this is a source/connectors validation set.\n")

    assets = [source_zip, firefox_xpi, chromium_zip, desktop_zip, branding_zip, validation_path, *android_artifacts]
    manifest_path = output / f"{prefix}-release-manifest.json"
    manifest = {
        "schemaVersion": 2,
        "product": "TabDeck",
        "version": version.name,
        "versionCode": version.code,
        "tag": version.tag,
        "releaseDate": version.release_date,
        "androidApkRequired": args.require_apk,
        "distribution": "github-community-release",
        "provenance": {
            "sourceCommit": source_commit or None,
            "releaseTag": release_tag,
            "signingCertificateSha256": signing_cert or None,
        },
        "assets": [
            {
                "name": path.name,
                "size": path.stat().st_size,
                "sha256": sha256(path),
                "mediaType": media_type(path),
            }
            for path in sorted(assets, key=lambda item: item.name)
        ],
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    assets.append(manifest_path)

    checksums = output / f"{prefix}-SHA256.txt"
    checksums.write_text(
        "".join(f"{sha256(path)}  {path.name}\n" for path in sorted(assets, key=lambda item: item.name)),
        encoding="utf-8",
    )
    assets.append(checksums)

    print(validator_output.strip())
    print(core_output.strip())
    for path in sorted(assets, key=lambda item: item.name):
        print(f"{path.name}\t{path.stat().st_size}\t{sha256(path)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
