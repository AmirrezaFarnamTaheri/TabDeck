#!/usr/bin/env python3
"""Shared public-version helpers for TabDeck release tooling."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "version.properties"
SEMVER_RE = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$")


@dataclass(frozen=True)
class ProductVersion:
    name: str
    code: int
    release_date: str

    @property
    def tag(self) -> str:
        return f"v{self.name}"

    @property
    def artifact_prefix(self) -> str:
        return f"TabDeck-v{self.name}"


def load_version(path: Path = VERSION_FILE) -> ProductVersion:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"{path}:{line_number}: expected KEY=VALUE")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in values:
            raise ValueError(f"{path}:{line_number}: missing or duplicate key {key!r}")
        values[key] = value

    missing = {"VERSION_NAME", "VERSION_CODE", "RELEASE_DATE"} - values.keys()
    unknown = values.keys() - {"VERSION_NAME", "VERSION_CODE", "RELEASE_DATE"}
    if missing:
        raise ValueError(f"Missing version properties: {', '.join(sorted(missing))}")
    if unknown:
        raise ValueError(f"Unknown version properties: {', '.join(sorted(unknown))}")

    name = values["VERSION_NAME"]
    if not SEMVER_RE.fullmatch(name):
        raise ValueError(f"VERSION_NAME is not semantic version syntax: {name!r}")

    try:
        code = int(values["VERSION_CODE"])
    except ValueError as exc:
        raise ValueError("VERSION_CODE must be an integer") from exc
    if not 1 <= code <= 2_100_000_000:
        raise ValueError("VERSION_CODE must be between 1 and 2,100,000,000")

    try:
        date.fromisoformat(values["RELEASE_DATE"])
    except ValueError as exc:
        raise ValueError("RELEASE_DATE must use YYYY-MM-DD") from exc

    return ProductVersion(name=name, code=code, release_date=values["RELEASE_DATE"])
