#!/usr/bin/env bash
# Host parity. Release assembly stays fail-closed until signing secrets are present.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export LICENSE_GATE_EXCLUDE="${LICENSE_GATE_EXCLUDE:-com.pirlruc.finsilo}"
bash scripts/check-analog-pins.sh
bash scripts/check-threshold-drift.sh
python3 scripts/read_kotlin_threshold.py statement_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py branch_coverage >/dev/null
python3 scripts/read_kotlin_threshold.py doc_coverage >/dev/null
python3 scripts/check-detekt-complexity.py
python3 scripts/check-lint-exceptions.py
python3 scripts/check-android-profile.py
python3 scripts/lint-doc-links.py --root "$ROOT"
bash scripts/run-shellcheck.sh
./gradlew :domain:ktlintCheck :domain:detekt :domain:test :domain:koverVerify :domain:dokkaGenerate
python3 scripts/check-kdoc-coverage.py
./gradlew :app:ktlintCheck :app:detekt :app:lintDebug :app:assembleDebug :app:testDebugUnitTest :app:koverVerifyDebug
bash scripts/run-gitleaks.sh
bash scripts/run-syft-sbom.sh
bash scripts/run-osv-scanner.sh
bash scripts/run-grype.sh
python3 scripts/check-licenses.py
bash scripts/run-mobsfscan.sh
