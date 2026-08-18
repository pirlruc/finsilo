#!/usr/bin/env bash
# Local parity for Phase 7 gates.
# Maintainability scans :domain and :app. Kover 95/95: :domain:koverVerify.
# CodeQL is CI-only (github/codeql-action); local SAST is semgrep + mobsfscan.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/read_kotlin_threshold.py statement_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py branch_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py doc_coverage >/dev/null
python3 scripts/check-detekt-complexity.py
python3 scripts/check-maintainability.py
./gradlew :domain:ktlintCheck :domain:detekt :domain:test :domain:koverVerify :domain:dokkaGenerate
python3 scripts/check-kdoc-coverage.py
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
bash scripts/run-gitleaks.sh
bash scripts/run-syft-sbom.sh
bash scripts/run-osv-scanner.sh
bash scripts/run-mobsfscan.sh
