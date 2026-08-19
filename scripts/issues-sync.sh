#!/usr/bin/env bash
# Thin wrapper so agents and humans can run github-scaffold issue sync from the repo root.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$ROOT/.github/scaffold/scripts/issues-sync.py" "$@"
