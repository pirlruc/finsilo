#!/usr/bin/env bash
# Produce a CycloneDX SBOM for a built APK (SC-SBOM-001/002) with checksum-pinned syft.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="1.27.1"
ARCHIVE="syft_${VERSION}_linux_amd64.tar.gz"
URL="https://github.com/anchore/syft/releases/download/v${VERSION}/${ARCHIVE}"
SHA256="c2cb5867a238baf41adf15f7e01e28cbd886378859eed81e52c080ca0346eefe"
WORKDIR="${TMPDIR:-/tmp}/finsilo-syft-${VERSION}"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OUT="${2:-$ROOT/app/build/reports/sbom.cdx.json}"
if [[ ! -f "$APK" ]]; then
  echo "Missing APK $APK (assembleDebug first)" >&2
  exit 1
fi
mkdir -p "$WORKDIR" "$(dirname "$OUT")"
if [[ ! -x "$WORKDIR/syft" ]]; then
  curl -fsSL "$URL" -o "$WORKDIR/$ARCHIVE"
  echo "${SHA256}  $WORKDIR/$ARCHIVE" | sha256sum -c -
  tar -xzf "$WORKDIR/$ARCHIVE" -C "$WORKDIR" syft
  chmod +x "$WORKDIR/syft"
fi
"$WORKDIR/syft" "$APK" -o "cyclonedx-json=$OUT" --quiet
echo "Wrote $OUT"
