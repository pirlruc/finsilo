#!/usr/bin/env python3
"""Measure KDoc coverage of public domain types and functions (KT-DOC-001)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from read_kotlin_threshold import read_threshold  # noqa: E402

SOURCE = ROOT / "domain" / "src" / "main" / "kotlin"
DECL = re.compile(
    r"^(?P<indent>\s*)(?P<mods>(?:(?:public|internal|private|protected|open|override|"
    r"suspend|inline|tailrec|operator|infix|abstract|inner|data|enum|sealed|companion)\s+)*)"
    r"(?P<kind>class|object|interface|fun)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)
SKIP_FUN = {"component1", "component2", "copy", "equals", "hashCode", "toString", "invoke"}


def has_kdoc(lines: list[str], index: int) -> bool:
    i = index - 1
    while i >= 0 and lines[i].strip() == "":
        i -= 1
    if i < 0:
        return False
    return lines[i].strip().endswith("*/")


def is_public(mods: str) -> bool:
    tokens = mods.split()
    if "private" in tokens or "internal" in tokens or "protected" in tokens:
        return False
    return True


def declarations(path: Path) -> list[tuple[str, bool]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    found: list[tuple[str, bool]] = []
    for index, line in enumerate(lines):
        stripped = line.split("//", 1)[0].rstrip()
        match = DECL.match(stripped)
        if match is None:
            continue
        kind = match.group("kind")
        name = match.group("name")
        mods = match.group("mods")
        indent = match.group("indent")
        if not is_public(mods):
            continue
        if kind == "fun" and indent:
            continue
        if kind == "fun" and name in SKIP_FUN:
            continue
        label = f"{path.relative_to(ROOT)}:{index + 1}:{kind} {name}"
        found.append((label, has_kdoc(lines, index)))
    return found


def main() -> int:
    required = read_threshold("doc_coverage")
    items: list[tuple[str, bool]] = []
    for path in sorted(SOURCE.rglob("*.kt")):
        items.extend(declarations(path))
    if not items:
        print("No public declarations found", file=sys.stderr)
        return 1
    documented = sum(1 for _, ok in items if ok)
    percent = 100.0 * documented / len(items)
    print(f"KDoc coverage: {documented}/{len(items)} = {percent:.1f}% (gate {required}%)")
    missing = [label for label, ok in items if not ok]
    if percent + 1e-9 < required:
        print("Undocumented public API:", file=sys.stderr)
        for label in missing:
            print(f"  {label}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
