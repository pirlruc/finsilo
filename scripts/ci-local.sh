#!/usr/bin/env bash
# Delegates to scripts/check-ci-local.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/scripts/check-ci-local.sh"
