#!/usr/bin/env bash
# Thin wrapper: sync every repo listed in docs/issues-sync-targets.yml.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/.github/scaffold/scripts/issues-sync-all.sh" "$@"
