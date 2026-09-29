#!/usr/bin/env python3
"""ANDROID-BUILD-002 / ANDROID-LINT-001 / ANDROID-SDK-001 / ANDROID-SDK-002.

Reads config/android.profile.thresholds.yml. When the guardrails submodule is
present, every analog key must match that copy (CI-022).
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONSUMER = ROOT / "config" / "android.profile.thresholds.yml"
ANALOG = ROOT / "docs" / "guardrails" / "android" / "profile.thresholds.yml"
APP_GRADLE = ROOT / "app" / "build.gradle.kts"
DOMAIN_GRADLE = ROOT / "domain" / "build.gradle.kts"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
CATALOG = ROOT / "gradle" / "libs.versions.toml"
CAPTURE_PERMISSIONS = (
    "android.permission.CAMERA",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_EXTERNAL_STORAGE",
)


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


def main() -> int:
    consumer = parse_int_keys(CONSUMER)
    if ANALOG.is_file():
        analog = parse_int_keys(ANALOG)
        for key, analog_value in analog.items():
            if consumer.get(key) != analog_value:
                raise SystemExit(
                    f"{CONSUMER.name} {key}={consumer.get(key)} != analog pin {analog_value}"
                )
    errors = consumer.get("android_lint_max_errors")
    if errors != 0:
        raise SystemExit(f"android_lint_max_errors={errors}; org default is 0")
    app_gradle = APP_GRADLE.read_text(encoding="utf-8")
    if "abortOnError = true" not in app_gradle:
        raise SystemExit("app lint.abortOnError is not true (ANDROID-LINT-001)")
    if not CATALOG.is_file():
        raise SystemExit("missing gradle/libs.versions.toml (ANDROID-BUILD-002)")
    for gradle in (APP_GRADLE, DOMAIN_GRADLE):
        for raw in gradle.read_text(encoding="utf-8").splitlines():
            stripped = raw.strip()
            if not stripped.startswith(("implementation(", "ksp(", "testImplementation(")):
                continue
            if "libs." in stripped or "project(" in stripped or "platform(" in stripped:
                continue
            raise SystemExit(f"{gradle.name}: dependency is not a catalog alias: {stripped}")
    manifest = MANIFEST.read_text(encoding="utf-8")
    if 'android:usesCleartextTraffic="false"' not in manifest:
        raise SystemExit("usesCleartextTraffic is not false (ANDROID-SDK-001)")
    nsc = ROOT / "app" / "src" / "main" / "res" / "xml" / "network_security_config.xml"
    if nsc.is_file() and 'cleartextTrafficPermitted="true"' in nsc.read_text(encoding="utf-8"):
        raise SystemExit("network security config permits cleartext (ANDROID-SDK-001)")
    for permission in CAPTURE_PERMISSIONS:
        if permission in manifest:
            raise SystemExit(f"capture permission {permission} is not used by this app (ANDROID-SDK-002)")
    print(f"android profile: lint max errors {errors}; catalog aliases; cleartext off")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
