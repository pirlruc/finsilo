#!/usr/bin/env bash
# Checksum-pinned grype on the Gradle CycloneDX BOM (KT-SEC-004).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="0.119.0"
ARCHIVE="grype_${VERSION}_linux_amd64.tar.gz"
URL="https://github.com/anchore/grype/releases/download/v${VERSION}/${ARCHIVE}"
SHA256="3fa2dc4b924621ab65404cf08d0b8438d896d80ab949c9d5a4ca283c36004c9b"
WORKDIR="${TMPDIR:-/tmp}/finsilo-grype-${VERSION}"
OUT_DIR="${GRYPE_OUT_DIR:-$ROOT/app/build/reports/grype}"
OUT="$OUT_DIR/grype.sarif"
BOM="${OSV_BOM:-$ROOT/build/reports/cyclonedx/bom.json}"
mkdir -p "$WORKDIR" "$OUT_DIR"
if [[ ! -x "$WORKDIR/grype" ]]; then
  curl -fsSL "$URL" -o "$WORKDIR/$ARCHIVE"
  echo "${SHA256}  $WORKDIR/$ARCHIVE" | sha256sum -c -
  tar -xzf "$WORKDIR/$ARCHIVE" -C "$WORKDIR" grype
  chmod +x "$WORKDIR/grype"
fi
if [[ ! -f "$BOM" ]]; then
  echo "error: grype needs CycloneDX BOM at $BOM (fail closed)" >&2
  exit 1
fi
"$WORKDIR/grype" "sbom:$BOM" -o sarif --file "$OUT"
if [[ ! -s "$OUT" ]]; then
  echo "error: grype wrote no SARIF (fail closed)" >&2
  exit 1
fi
echo "Wrote $OUT"
python3 "$ROOT/scripts/fail-on-sarif-severity.py" --min-severity high "$OUT"
