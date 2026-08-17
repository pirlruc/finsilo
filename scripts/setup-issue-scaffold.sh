#!/usr/bin/env bash
# Thin wrapper: create methodology labels and milestones from docs/issues-sync-targets.yml.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/.github/scaffold/scripts/setup-issue-scaffold.sh" "$@"
