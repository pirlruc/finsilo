#!/usr/bin/env bash
# Thin wrapper: copy github-scaffold templates into this repo (seed-once files are kept).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONSUMING_REPO_ROOT="$ROOT" exec bash "$ROOT/.github/scaffold/scripts/sync-templates.sh" "$@"
