#!/usr/bin/env python3
"""Validate TabDeck benchmark results against checked-in release budgets."""
from __future__ import annotations
import argparse, json, math
from pathlib import Path

DEFAULT_CONFIG = Path(__file__).resolve().parents[1] / "tools" / "performance-budgets.json"


def finite_number(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f"{label} must be a finite number")
    return float(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--results", type=Path)
    parser.add_argument("--validate-config", action="store_true")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    metrics = config.get("metrics", {})
    if not metrics:
        raise SystemExit("No performance metrics configured")
    for name, rule in metrics.items():
        if rule.get("direction") not in {"max", "min"}:
            raise SystemExit(f"{name}: direction must be max or min")
        finite_number(rule.get("threshold"), f"{name}.threshold")
        if not rule.get("method") or not rule.get("dataset") or not rule.get("deviceClass"):
            raise SystemExit(f"{name}: method, dataset, and deviceClass are required")
    if args.validate_config and args.results is None:
        print(f"Validated {len(metrics)} performance budgets")
        return 0
    if args.results is None:
        raise SystemExit("--results is required unless --validate-config is used")
    results = json.loads(args.results.read_text(encoding="utf-8"))
    failures = []
    for name, rule in metrics.items():
        actual = finite_number(results.get(name), name)
        threshold = finite_number(rule["threshold"], f"{name}.threshold")
        passed = actual <= threshold if rule["direction"] == "max" else actual >= threshold
        print(f"{'PASS' if passed else 'FAIL'} {name}: {actual} ({rule['direction']} {threshold})")
        if not passed:
            failures.append(name)
    if failures:
        raise SystemExit("Performance budget failures: " + ", ".join(failures))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
