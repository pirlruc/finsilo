#!/usr/bin/env bash
# Container stand-in for tools the host parity script does not run.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if ! command -v docker >/dev/null 2>&1; then
    echo "docker is not available; host script scripts/check-ci-local.sh is the gate" >&2
    exit 0
fi
echo "No extra container tools are queued. Host parity is scripts/check-ci-local.sh."
