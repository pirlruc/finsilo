#!/usr/bin/env python3
"""KT-BUILD-002: lint exceptions expire within lint_exception_max_days.

A dated exception uses a nearby comment:

    lint-exception-until: YYYY-MM-DD

The date must be today or later, and no further than lint_exception_max_days ahead.

One platform case is not a timed waiver: @Suppress("DEPRECATION") next to a
comment containing "lint-exception: platform-api". That call is the pre-API-30
Keystore API while minSdk stays 26 (FS-DEC-001). It is listed in
docs/scanner-exceptions.md. It is not a lowered numeric gate.
"""
from __future__ import annotations

import datetime as dt
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from read_kotlin_threshold import read_threshold  # noqa: E402

SOURCES = (
    ROOT / "domain" / "src" / "main",
    ROOT / "app" / "src" / "main",
)
SUPPRESS = re.compile(r"@Suppress(?:Warnings)?\s*\(([^)]*)\)|ktlint-disable[^\n]*")
UNTIL = re.compile(r"lint-exception-until:\s*(\d{4}-\d{2}-\d{2})")
PLATFORM = "lint-exception: platform-api"


def kotlin_files() -> list[Path]:
    files: list[Path] = []
    for source in SOURCES:
        files.extend(source.rglob("*.kt"))
    return sorted(files)


def nearby(lines: list[str], index: int) -> str:
    start = max(0, index - 4)
    return "\n".join(lines[start : index + 1])


def main() -> int:
    max_days = read_threshold("lint_exception_max_days")
    today = dt.date.today()
    latest = today + dt.timedelta(days=max_days)
    errors: list[str] = []
    dated = 0
    platform = 0
    for path in kotlin_files():
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines):
            if not SUPPRESS.search(line):
                continue
            window = nearby(lines, index)
            until = UNTIL.search(window)
            if until:
                try:
                    expiry = dt.date.fromisoformat(until.group(1))
                except ValueError:
                    errors.append(f"{path}:{index + 1}: bad lint-exception-until date")
                    continue
                dated += 1
                if expiry < today:
                    errors.append(f"{path}:{index + 1}: lint exception expired {expiry.isoformat()}")
                elif expiry > latest:
                    errors.append(
                        f"{path}:{index + 1}: lint-exception-until {expiry.isoformat()} "
                        f"is more than {max_days} days out"
                    )
                continue
            if '@Suppress("DEPRECATION")' in line and PLATFORM in window:
                platform += 1
                continue
            errors.append(
                f"{path}:{index + 1}: lint exception needs lint-exception-until "
                f"within {max_days} days, or a platform-api comment for DEPRECATION"
            )
    if errors:
        print("KT-BUILD-002 lint exceptions:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1
    print(f"lint exceptions: {dated} dated, {platform} platform-api, max {max_days} days")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
