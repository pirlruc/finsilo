#!/usr/bin/env python3
"""Fail-closed reader for Kotlin numeric gates (CI-022).

CI on this public repo cannot clone the private analog submodule, so the
committed copy at config/kotlin.profile.thresholds.yml is what jobs read.
When docs/guardrails/kotlin/profile.thresholds.yml is present, every key must
match that pin (no silent drift).
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONSUMER = ROOT / "config" / "kotlin.profile.thresholds.yml"
ANALOG = ROOT / "docs" / "guardrails" / "kotlin" / "profile.thresholds.yml"


def parse_int_keys(path: Path) -> dict[str, int]:
    if not path.is_file():
        raise SystemExit(f"Missing {path} (CI-022 fail closed)")
    values: dict[str, int] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or ":" not in line:
            continue
        key, raw_value = line.split(":", 1)
        key = key.strip()
        value = raw_value.strip()
        if not key or not value:
            raise SystemExit(f"Empty key or value in {path}: {raw!r} (CI-022 fail closed)")
        try:
            values[key] = int(value)
        except ValueError as exc:
            raise SystemExit(f"Non-integer '{key}' in {path}: {value}") from exc
    if not values:
        raise SystemExit(f"No keys in {path} (CI-022 fail closed)")
    return values


def load_thresholds() -> dict[str, int]:
    consumer = parse_int_keys(CONSUMER)
    if ANALOG.is_file():
        analog = parse_int_keys(ANALOG)
        for key, analog_value in analog.items():
            consumer_value = consumer.get(key)
            if consumer_value != analog_value:
                raise SystemExit(
                    f"{CONSUMER.name} {key}={consumer_value} != analog pin {analog_value} "
                    f"({ANALOG}). Update the consumer copy when bumping docs/guardrails."
                )
    return consumer


def read_threshold(key: str) -> int:
    values = load_thresholds()
    if key not in values:
        raise SystemExit(f"Missing key '{key}' in {CONSUMER} (CI-022 fail closed)")
    return values[key]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <key>")
    print(read_threshold(sys.argv[1]))
