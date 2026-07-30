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

EXCLUDED_PARTS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "dist",
    "__pycache__",
}
EXCLUDED_NAMES = {
    "local.properties",
}
EXCLUDED_SUFFIXES = {
    ".iml",
    ".jks",
    ".keystore",
    ".key",
    ".p12",
    ".pfx",
    ".pem",
    ".pyc",
    ".pyo",
}


def run_checked(command: list[str], cwd: Path = ROOT) -> str:
    process = subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)
    output = process.stdout + process.stderr
    if process.returncode != 0:
        raise RuntimeError(f"Command failed ({process.returncode}): {' '.join(command)}\n{output}")
    return output


def fixed_time(version: ProductVersion) -> tuple[int, int, int, int, int, int]:
    year, month, day = (int(part) for part in version.release_date.split("-"))
    # ZIP timestamps cannot be earlier than 1980 and are stored at two-second precision.
    return max(1980, year), month, day, 12, 0, 0


def should_include(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if any(part in EXCLUDED_PARTS for part in relative.parts):
        return False
    if path.name in EXCLUDED_NAMES or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    if path.is_symlink() or not path.is_file():
        return False
    return True


def zip_info(archive_name: str, executable: bool, timestamp: tuple[int, int, int, int, int, int]) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(archive_name, timestamp)
    info.compress_type = zipfile.ZIP_DEFLATED
    permissions = 0o755 if executable else 0o644
    info.external_attr = (stat.S_IFREG | permissions) << 16
    info.create_system = 3
    return info


def is_executable(path: Path) -> bool:
    return bool(path.stat().st_mode & stat.S_IXUSR) or path.name == "gradlew" or path.suffix == ".sh"


def write_archive(
    destination: Path,
    entries: list[tuple[Path, str]],
    timestamp: tuple[int, int, int, int, int, int],
) -> None:
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
    return [
        (path, f"{prefix}/{path.relative_to(ROOT).as_posix()}")
        for path in ROOT.rglob("*")
        if should_include(path)
    ]


def folder_entries(folder: Path, prefix: str = "") -> list[tuple[Path, str]]:
    entries: list[tuple[Path, str]] = []
    for path in folder.rglob("*"):
        if path.is_file() and not path.is_symlink() and "__pycache__" not in path.parts:
            relative = path.relative_to(folder).as_posix()
            entries.append((path, f"{prefix}{relative}"))
    return entries


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def media_type(path: Path) -> str:
    overrides = {
        ".apk": "application/vnd.android.package-archive",
        ".aab": "application/octet-stream",
        ".xpi": "application/x-xpinstall",
    }
    return overrides.get(path.suffix.lower(), mimetypes.guess_type(path.name)[0] or "application/octet-stream")


def copy_android_artifacts(output: Path, prefix: str, required: bool) -> list[Path]:
    candidates = [
        (ROOT / "app/build/outputs/apk/release/app-release.apk", output / f"{prefix}.apk"),
        (ROOT / "app/build/outputs/bundle/release/app-release.aab", output / f"{prefix}.aab"),
    ]
    missing = [source for source, _ in candidates if not source.is_file()]
    if missing and required:
        raise RuntimeError("Required Android release artifacts are missing: " + ", ".join(str(p) for p in missing))

    copied: list[Path] = []
    for source, destination in candidates:
        if source.is_file():
            shutil.copyfile(source, destination)
            copied.append(destination)

    mapping = ROOT / "app/build/outputs/mapping/release/mapping.txt"
    if mapping.is_file():
        destination = output / f"{prefix}-mapping.txt"
        shutil.copyfile(mapping, destination)
        copied.append(destination)
    return copied


def verify_archive_entries(required: dict[Path, list[str]]) -> None:
    for archive_path, expected in required.items():
        with zipfile.ZipFile(archive_path) as archive:
            missing = [entry for entry in expected if entry not in archive.namelist()]
            if missing:
                raise RuntimeError(f"{archive_path.name} missing required entries: {missing}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=ROOT / "dist")
    parser.add_argument(
        "--require-android-artifacts",
        action="store_true",
        help="Fail unless a release APK and AAB already exist under app/build/outputs",
    )
    args = parser.parse_args()

    version = load_version()
    prefix = version.artifact_prefix
    archive_root = prefix
    timestamp = fixed_time(version)
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)

    # Avoid stale assets being mistaken for the current release.
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

    write_archive(source_zip, source_entries(archive_root), timestamp)
    write_archive(firefox_xpi, folder_entries(ROOT / "extensions/firefox-android"), timestamp)
    write_archive(chromium_zip, folder_entries(ROOT / "extensions/chromium-desktop"), timestamp)
    desktop_root = f"TabDeck-Desktop-Link-v{version.name}/"
    write_archive(desktop_zip, folder_entries(ROOT / "desktop-link", prefix=desktop_root), timestamp)

    required = {
        source_zip: [
            f"{archive_root}/README.md",
            f"{archive_root}/version.properties",
            f"{archive_root}/app/build.gradle.kts",
            f"{archive_root}/.github/workflows/ci.yml",
            f"{archive_root}/.github/workflows/release.yml",
            f"{archive_root}/tools/package_release.py",
        ],
        firefox_xpi: ["manifest.json", "popup.html", "popup.js"],
        chromium_zip: ["manifest.json", "popup.html", "popup.js"],
        desktop_zip: [f"{desktop_root}TabDeckLink.ps1", f"{desktop_root}README.md"],
    }
    verify_archive_entries(required)

    android_artifacts = copy_android_artifacts(output, prefix, args.require_android_artifacts)

    with validation_path.open("a", encoding="utf-8") as report:
        report.write("\nExecutable core harness\n=======================\n")
        report.write(core_output.strip() + "\n")
        report.write("\nPackaging verification\n======================\n")
        for archive_path in required:
            with zipfile.ZipFile(archive_path) as archive:
                report.write(
                    f"PASS: {archive_path.name}: {len(archive.namelist())} unique entries; CRC test passed\n"
                )
        if android_artifacts:
            report.write("PASS: Android release APK/AAB were present and copied into the release set.\n")
        else:
            report.write(
                "INFO: No Android binaries were packaged. This is a source-only release set, not an installable release.\n"
            )
        report.write(
            "Signature verification, Android lint/tests, and assembly are performed by the GitHub release workflow "
            "before invoking this packager with --require-android-artifacts.\n"
        )

    assets = [source_zip, firefox_xpi, chromium_zip, desktop_zip, validation_path, *android_artifacts]
    manifest_path = output / f"{prefix}-release-manifest.json"
    manifest = {
        "schemaVersion": 1,
        "product": "TabDeck",
        "version": version.name,
        "versionCode": version.code,
        "tag": version.tag,
        "releaseDate": version.release_date,
        "androidArtifactsRequired": args.require_android_artifacts,
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
