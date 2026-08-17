#!/usr/bin/env python3
"""Fail closed on min_maintainability_index for production Kotlin (KT-CPLX-002).

Tool: multimetric (Kotlin-capable). Formula: SEI / Oman-Hagemeister (same 171-scale
as radon and Metrix++). Gate is the worst *file* MI, not the concatenated overall.
Scans :domain and :app main sources.
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from read_kotlin_threshold import read_threshold  # noqa: E402

SOURCES = (
    ROOT / "domain" / "src" / "main" / "kotlin",
    ROOT / "app" / "src" / "main" / "java",
)
MULTIMETRIC = "2.4.4"


def kotlin_files() -> list[str]:
    files: list[str] = []
    for source in SOURCES:
        files.extend(str(path) for path in source.rglob("*.kt"))
    return sorted(files)


def main() -> int:
    required = read_threshold("min_maintainability_index")
    files = kotlin_files()
    if not files:
        print("No Kotlin sources under domain/ or app/", file=sys.stderr)
        return 1
    try:
        proc = subprocess.run(
            [sys.executable, "-m", "multimetric", "--maintindex", "sei", *files],
            check=False,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        print(f"multimetric missing; pip install multimetric=={MULTIMETRIC}", file=sys.stderr)
        return 1
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr or proc.stdout)
        return proc.returncode
    payload = json.loads(proc.stdout)
    files_stats = payload.get("files") or {}
    if not files_stats:
        print("multimetric returned no per-file stats", file=sys.stderr)
        return 1
    scores = []
    for path, stats in files_stats.items():
        mi = stats.get("maintainability_index")
        if mi is None:
            print(f"Missing maintainability_index for {path}", file=sys.stderr)
            return 1
        scores.append((float(mi), path))
    scores.sort()
    lowest, worst = scores[0]
    print(f"SEI maintainability: min {lowest:.3f} (gate {required}) worst={worst}")
    if lowest + 1e-9 < required:
        print("Files below gate:", file=sys.stderr)
        for mi, path in scores:
            if mi + 1e-9 < required:
                print(f"  {mi:.3f}  {path}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
