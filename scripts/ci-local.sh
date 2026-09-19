#!/usr/bin/env bash
# Local parity for Phase 7 gates.
# Maintainability scans :domain and :app. Kover 95/95: :domain:koverVerify.
# CodeQL is CI-only (github/codeql-action); local SAST is semgrep + mobsfscan.
# grype is the KT-SEC-004 gate; OSV Scanner remains complementary.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/check-analog-pins.sh
python3 scripts/read_kotlin_threshold.py statement_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py branch_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py doc_coverage >/dev/null
python3 scripts/check-detekt-complexity.py
python3 scripts/check-maintainability.py
python3 scripts/lint-doc-links.py --root "$ROOT"
bash scripts/run-shellcheck.sh
./gradlew :domain:ktlintCheck :domain:detekt :domain:test :domain:koverVerify :domain:dokkaGenerate
python3 scripts/check-kdoc-coverage.py
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
bash scripts/run-gitleaks.sh
bash scripts/run-syft-sbom.sh
bash scripts/run-osv-scanner.sh
bash scripts/run-grype.sh
python3 scripts/check-licenses.py
bash scripts/run-mobsfscan.sh
