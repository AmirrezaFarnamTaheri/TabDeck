#!/usr/bin/env python3
"""Add concise function docstrings to TabDeck's Python tooling."""
from __future__ import annotations

import ast
import sys
from pathlib import Path

TARGET = Path(sys.argv[1]).resolve()

DESCRIPTIONS = {
    "check": "Record a version-contract failure when the condition is false.",
    "sync_extensions": "Synchronize extension manifest versions with the product version.",
    "main": "Run the command-line entry point and return its exit status.",
    "run_checked": "Run a subprocess and raise an actionable error when it fails.",
    "fixed_time": "Return the deterministic archive timestamp used for release packaging.",
    "should_include": "Return whether a repository path belongs in a source archive.",
    "zip_info": "Build deterministic ZIP metadata for an archive entry.",
    "is_executable": "Return whether a path should retain executable permissions in archives.",
    "write_archive": "Write a deterministic ZIP archive from the supplied entries.",
    "source_entries": "Collect deterministic source-archive entries from the repository.",
    "folder_entries": "Collect deterministic archive entries from a repository folder.",
    "sha256": "Return the SHA-256 digest of a file.",
    "media_type": "Return the release media type for an artifact path.",
    "copy_android_artifacts": "Copy verified Android APK and AAB outputs into the release directory.",
    "verify_archive_entries": "Verify that an archive contains the required release entries.",
    "finite_number": "Validate and normalize a finite numeric performance result.",
    "fail": "Record a validation failure for the final project report.",
    "warn": "Record a validation warning for the final project report.",
    "ok": "Record a successful validation check for the final project report.",
    "run": "Run a repository command and capture its output without raising automatically.",
    "files": "Collect matching repository files while excluding generated directories.",
    "validate_xml": "Parse all repository XML files and report malformed documents.",
    "validate_json": "Parse all repository JSON files and report malformed documents.",
    "error": "Raise an HTML parsing error for compatibility with older Python versions.",
    "validate_html": "Parse all repository HTML files with the strict parser.",
    "validate_javascript": "Check JavaScript syntax with Node when it is available.",
    "validate_shell": "Check shell-script syntax with the system shell when available.",
    "validate_embedded_xaml": "Parse XAML documents embedded in PowerShell here-strings.",
    "strip_kotlin_comments_and_strings": "Remove Kotlin lexical regions while preserving line counts.",
    "balanced_delimiters": "Check balanced delimiters and return failure location details.",
    "validate_kotlin_structure": "Check Kotlin and Gradle source structure for corruption.",
    "validate_powershell_structure": "Parse PowerShell files when a compatible runtime is available.",
    "validate_manifest_contracts": "Validate Android manifest privacy and browser-discovery contracts.",
    "validate_product_version": "Validate the authoritative product version and synchronized consumers.",
    "validate_extension_contracts": "Validate extension manifests and synchronized shared runtime files.",
    "validate_gradle_wrapper_files": "Validate the committed Gradle wrapper artifacts.",
    "validate_build_coordinates": "Validate pinned build-tool and dependency coordinates.",
    "validate_text_integrity": "Validate UTF-8 encoding and reject unexpected control bytes.",
    "validate_compose_icon_imports": "Validate that used Compose icons have explicit imports.",
    "validate_release_contracts": "Validate cross-component runtime and release invariants.",
    "validate_workflow_contracts": "Validate CI and release workflow safety contracts.",
    "validate_no_obvious_secrets": "Scan source and documentation for obvious embedded secrets.",
    "load_version": "Load and validate the authoritative version properties.",
    "tag": "Return the semantic release tag for this version.",
    "artifact_prefix": "Return the deterministic artifact-name prefix for this version.",
}


def fallback_description(name: str) -> str:
    """Return a readable fallback docstring for an unmapped function name."""
    words = name.replace("_", " ").strip()
    return f"Run the {words} operation."


def add_docstrings(path: Path) -> tuple[int, int]:
    """Insert missing function docstrings and return total and inserted counts."""
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source, filename=str(path))
    lines = source.splitlines(keepends=True)
    insertions: list[tuple[int, str]] = []
    total = 0
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        total += 1
        if ast.get_docstring(node, clean=False) is not None:
            continue
        if not node.body:
            raise SystemExit(f"{path}: function {node.name} has no body")
        first_line = node.body[0].lineno - 1
        existing = lines[first_line]
        indent = existing[: len(existing) - len(existing.lstrip())]
        description = DESCRIPTIONS.get(node.name, fallback_description(node.name))
        insertions.append((first_line, f'{indent}"""{description}"""\n'))
    for index, text in sorted(insertions, reverse=True):
        lines.insert(index, text)
    if insertions:
        path.write_text("".join(lines), encoding="utf-8")
    return total, len(insertions)


def coverage(paths: list[Path]) -> tuple[int, int]:
    """Return total and documented function counts for the supplied Python files."""
    total = 0
    documented = 0
    for path in paths:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                total += 1
                documented += int(ast.get_docstring(node, clean=False) is not None)
    return total, documented


def main() -> int:
    """Document project Python functions and enforce at least 80 percent coverage."""
    paths = sorted((TARGET / "tools").glob("*.py"))
    if not paths:
        raise SystemExit("No Python tools found")
    inserted = 0
    for path in paths:
        _, added = add_docstrings(path)
        inserted += added
    total, documented = coverage(paths)
    ratio = documented / total if total else 1.0
    print(f"Python docstrings: {documented}/{total} ({ratio:.1%}); inserted {inserted}")
    if ratio < 0.80:
        raise SystemExit("Python function docstring coverage remains below 80%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
