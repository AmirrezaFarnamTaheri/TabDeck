#!/usr/bin/env python3
"""Validate that browser connector JavaScript binds only existing popup controls."""

from __future__ import annotations

from html.parser import HTMLParser
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CONNECTORS = (
    ROOT / "extensions/chromium-desktop",
    ROOT / "extensions/firefox-android",
)


class IdCollector(HTMLParser):
    """Collect unique HTML element IDs from a popup document."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: set[str] = set()
        self.duplicates: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        """Record IDs and detect duplicate declarations."""
        del tag
        for name, value in attrs:
            if name != "id" or not value:
                continue
            if value in self.ids:
                self.duplicates.add(value)
            self.ids.add(value)


CONTROL_ARRAY = re.compile(
    r"const\s+REQUIRED_CONTROLS\s*=\s*\[(?P<body>.*?)\];",
    flags=re.DOTALL,
)
STRING_LITERAL = re.compile(r"['\"]([A-Za-z][A-Za-z0-9_-]*)['\"]")
DOLLAR_SELECTOR = re.compile(r"\$\(\s*['\"]#([A-Za-z][A-Za-z0-9_-]*)['\"]\s*\)")
GET_BY_ID = re.compile(r"getElementById\(\s*['\"]([A-Za-z][A-Za-z0-9_-]*)['\"]\s*\)")


def javascript_control_ids(source: str) -> set[str]:
    """Extract literal popup-control IDs referenced by connector JavaScript."""
    found = set(DOLLAR_SELECTOR.findall(source))
    found.update(GET_BY_ID.findall(source))
    control_array = CONTROL_ARRAY.search(source)
    if control_array:
        found.update(STRING_LITERAL.findall(control_array.group("body")))
    return found


def validate_connector(folder: Path) -> list[str]:
    """Return DOM-contract failures for one connector package."""
    html_path = folder / "popup.html"
    js_path = folder / "popup.js"
    parser = IdCollector()
    parser.feed(html_path.read_text(encoding="utf-8"))
    javascript_ids = javascript_control_ids(js_path.read_text(encoding="utf-8"))
    failures: list[str] = []
    if parser.duplicates:
        failures.append(f"duplicate popup IDs: {', '.join(sorted(parser.duplicates))}")
    missing = javascript_ids - parser.ids
    if missing:
        failures.append(f"JavaScript references missing popup IDs: {', '.join(sorted(missing))}")
    if not javascript_ids:
        failures.append("no JavaScript popup-control bindings were detected")
    logo = folder / "tabdeck-mark.svg"
    if not logo.is_file() or 'src="tabdeck-mark.svg"' not in html_path.read_text(encoding="utf-8"):
        failures.append("the branded popup logo is missing or not rendered")
    return failures


def main() -> int:
    """Validate all connector DOM contracts and return an executable status."""
    failures: list[str] = []
    for folder in CONNECTORS:
        connector_failures = validate_connector(folder)
        relative = folder.relative_to(ROOT)
        for failure in connector_failures:
            failures.append(f"{relative}: {failure}")
    if failures:
        raise SystemExit("\n".join(failures))
    print(f"Validated popup DOM bindings and branding for {len(CONNECTORS)} connectors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
