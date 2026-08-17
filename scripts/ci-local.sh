#!/usr/bin/env bash
# Local parity for Phase 7 gates that currently pass.
# Kover 95/95 is wired but not yet green: ./gradlew :domain:koverVerify
# (docs/limitations.md LIM-COV). Maintainability scans :domain and :app.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/read_kotlin_threshold.py statement_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py branch_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py doc_coverage >/dev/null
python3 scripts/check-detekt-complexity.py
python3 scripts/check-maintainability.py
./gradlew :domain:ktlintCheck :domain:detekt :domain:test :domain:koverLog :domain:dokkaGenerate
python3 scripts/check-kdoc-coverage.py
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
bash scripts/run-gitleaks.sh
bash scripts/run-syft-sbom.sh
