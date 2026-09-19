#!/usr/bin/env python3
"""Fail-closed SPDX license gate on the Gradle CycloneDX BOM (SC-LIC-001)."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOM = Path(
    __import__("os").environ.get("OSV_BOM", str(ROOT / "build" / "reports" / "cyclonedx" / "bom.json"))
)
ANALOG = ROOT / "docs" / "guardrails" / "supply-chain" / "profile.thresholds.yml"
CONSUMER = ROOT / "config" / "supply-chain.profile.thresholds.yml"


def parse_list(path: Path, key: str) -> list[str]:
    if not path.is_file():
        raise SystemExit(f"Missing {path} (CI-022 fail closed)")
    values: list[str] = []
    in_list = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        stripped = line.strip()
        if not in_list:
            if not stripped.startswith(f"{key}:"):
                continue
            rest = stripped.split(":", 1)[1].strip()
            if rest in {"[]", ""}:
                return []
            in_list = True
            continue
        if stripped.startswith("- "):
            values.append(stripped[2:].strip().strip("'\""))
            continue
        if stripped.endswith(":") or not stripped:
            break
    return values


def load_policy() -> tuple[list[str], list[str]]:
    path = ANALOG if ANALOG.is_file() else CONSUMER
    return parse_list(path, "license_deny_list"), parse_list(path, "license_allow_list")


def component_licenses(component: dict) -> list[str]:
    found: list[str] = []
    for entry in component.get("licenses") or []:
        license_obj = entry.get("license") or {}
        for field in ("id", "name"):
            value = license_obj.get(field)
            if value:
                found.append(str(value))
        expression = entry.get("expression")
        if expression:
            found.append(str(expression))
    return found


def main() -> int:
    deny, allow = load_policy()
    if not BOM.is_file():
        print(f"Missing CycloneDX BOM at {BOM} (fail closed)", file=sys.stderr)
        return 1
    payload = json.loads(BOM.read_text(encoding="utf-8"))
    components = payload.get("components") or []
    if not components:
        print("CycloneDX BOM has no components (fail closed)", file=sys.stderr)
        return 1
    denied: list[str] = []
    disallowed: list[str] = []
    for component in components:
        name = str(component.get("bom-ref") or component.get("name") or "unknown")
        licenses = component_licenses(component)
        blob = " ".join(licenses)
        for needle in deny:
            if needle and needle.lower() in blob.lower():
                denied.append(f"{name}: {blob}")
        if allow and licenses and not any(item in allow for item in licenses):
            disallowed.append(f"{name}: {blob}")
    print(f"SC-LIC-001: {len(components)} components, deny={len(deny)} allow={len(allow)}")
    if denied:
        print("Denied licenses:", file=sys.stderr)
        for row in denied:
            print(f"  {row}", file=sys.stderr)
        return 1
    if disallowed:
        print("Licenses not on allow list:", file=sys.stderr)
        for row in disallowed:
            print(f"  {row}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
