#!/usr/bin/env bash
# Local/CI runner for MobSF's source scanner (mobsfscan).
# This is the CI-practical Mobile Security Framework gate: Kotlin/Android SAST
# without Docker. Full MobSF APK analysis needs the MobSF container (not in CI).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="1.0.0"
VENV="${TMPDIR:-/tmp}/finsilo-mobsfscan-${VERSION}"
OUT="${MOBSF_SARIF:-$ROOT/app/build/reports/mobsfscan.sarif}"
mkdir -p "$(dirname "$OUT")"

install_mobsfscan() {
  if python3 -m venv "$VENV" >/dev/null 2>&1; then
    "$VENV/bin/pip" install --disable-pip-version-check --quiet "mobsfscan==${VERSION}"
    echo "$VENV/bin/mobsfscan"
    return
  fi
  python3 -m pip install --user --disable-pip-version-check --quiet "mobsfscan==${VERSION}"
  if command -v mobsfscan >/dev/null 2>&1; then
    command -v mobsfscan
    return
  fi
  echo "$HOME/.local/bin/mobsfscan"
}

if [[ -x "$VENV/bin/mobsfscan" ]]; then
  MOBSF="$VENV/bin/mobsfscan"
elif command -v mobsfscan >/dev/null 2>&1; then
  MOBSF="$(command -v mobsfscan)"
else
  MOBSF="$(install_mobsfscan)"
fi
if [[ ! -x "$MOBSF" ]]; then
  echo "error: mobsfscan not installed at $MOBSF" >&2
  exit 1
fi
cd "$ROOT"
"$MOBSF" \
  --type android \
  --sarif \
  -o "$OUT" \
  --no-fail \
  "$ROOT/app/src/main" \
  "$ROOT/domain/src/main"
if [[ ! -s "$OUT" ]]; then
  echo "error: mobsfscan wrote no SARIF (fail closed)" >&2
  exit 1
fi
echo "Wrote $OUT"
python3 "$ROOT/scripts/fail-on-sarif-severity.py" --min-severity high "$OUT"
