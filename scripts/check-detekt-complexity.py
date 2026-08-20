#!/usr/bin/env python3
"""Fail closed if detekt CyclomaticComplexMethod disagrees with the Kotlin profile (CI-022)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from read_kotlin_threshold import read_threshold  # noqa: E402

DETEKT = ROOT / "config" / "detekt" / "detekt.yml"
RULE = re.compile(
    r"CyclomaticComplexMethod:\s*\n(?:[ \t]+.+\n)*?[ \t]+allowedComplexity:\s*(\d+)",
    re.MULTILINE,
)


def main() -> int:
    allowed = read_threshold("max_cyclomatic_complexity")
    # detekt 2.x reports when complexity > allowedComplexity (inclusive max).
    text = DETEKT.read_text(encoding="utf-8")
    match = RULE.search(text)
    if match is None:
        print(f"Missing CyclomaticComplexMethod.allowedComplexity in {DETEKT}", file=sys.stderr)
        return 1
    actual = int(match.group(1))
    if actual != allowed:
        print(
            f"detekt CyclomaticComplexMethod.allowedComplexity={actual} but profile "
            f"max_cyclomatic_complexity={allowed}",
            file=sys.stderr,
        )
        return 1
    print(f"detekt complexity gate: max {allowed} (allowedComplexity {actual})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
