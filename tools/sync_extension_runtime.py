#!/usr/bin/env python3
"""Synchronize the canonical extension bridge runtime into both extension packages."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "extensions/shared/bridge-runtime.js"
content = source.read_text(encoding="utf-8")
for destination in (
    root / "extensions/chromium-desktop/bridge-runtime.js",
    root / "extensions/firefox-android/bridge-runtime.js",
):
    destination.write_text(content, encoding="utf-8")
print("Synchronized extension bridge runtime")
