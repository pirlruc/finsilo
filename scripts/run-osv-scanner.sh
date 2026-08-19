#!/usr/bin/env bash
# Local/CI runner for a checksum-pinned OSV Scanner binary (KT-SEC-002).
# Complements GitHub dependency-review (PR diff / GH Advisory). Gradle has no
# lockfile, so this gate scans the CycloneDX BOM from the Gradle plugin
# (resolved direct + transitive graph).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="2.5.1"
BIN="osv-scanner_linux_amd64"
URL="https://github.com/google/osv-scanner/releases/download/v${VERSION}/${BIN}"
SHA256="f9f25499a2c8cc367b3af45df2ea7eeca7fbccceab9c35079968f4b3652194be"
WORKDIR="${TMPDIR:-/tmp}/finsilo-osv-scanner-${VERSION}"
OUT_DIR="${OSV_OUT_DIR:-$ROOT/app/build/reports/osv}"
OUT="$OUT_DIR/osv-scanner.sarif"
BOM="${OSV_BOM:-$ROOT/build/reports/cyclonedx/bom.json}"
mkdir -p "$WORKDIR" "$OUT_DIR"
if [[ ! -x "$WORKDIR/osv-scanner" ]]; then
  curl -fsSL "$URL" -o "$WORKDIR/$BIN"
  echo "${SHA256}  $WORKDIR/$BIN" | sha256sum -c -
  chmod +x "$WORKDIR/$BIN"
  mv "$WORKDIR/$BIN" "$WORKDIR/osv-scanner"
fi
cd "$ROOT"
./gradlew cyclonedxBom --stacktrace
if [[ ! -f "$BOM" ]]; then
  echo "error: OSV Scanner needs CycloneDX BOM at $BOM (fail closed)" >&2
  exit 1
fi

status=0
"$WORKDIR/osv-scanner" scan source -L "$BOM" --format sarif --output-file "$OUT" || status=$?
# 0 = clean, 1 = vulnerabilities reported (severity gate is the SARIF script).
if [[ "$status" -gt 1 ]]; then
  exit "$status"
fi
if [[ ! -s "$OUT" ]]; then
  echo "error: OSV Scanner wrote no SARIF (fail closed)" >&2
  exit 1
fi
echo "Wrote $OUT"
python3 "$ROOT/scripts/fail-on-sarif-severity.py" --min-severity high "$OUT"
