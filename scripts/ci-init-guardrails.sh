#!/usr/bin/env bash
# CI-only: init docs/guardrails (pirlruc/guardrails pin).
# Do not init .github/scaffold — templates and Cursor rules are synced into this
# repo. Scaffold is for local issues-sync / sync-templates only.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ANALOG="docs/guardrails/kotlin/profile.thresholds.yml"

if [[ -z "${GUARDRAILS_READ_TOKEN:-}" ]]; then
  echo "GUARDRAILS_READ_TOKEN unset; reading config/kotlin.profile.thresholds.yml"
  exit 0
fi

# Rewrite only the analog HTTPS URL so other GitHub fetches do not see the PAT.
git -c "url.https://x-access-token:${GUARDRAILS_READ_TOKEN}@github.com/pirlruc/guardrails.git.insteadOf=https://github.com/pirlruc/guardrails.git" \
  submodule update --init docs/guardrails

if [[ ! -f "${ANALOG}" ]]; then
  echo "missing ${ANALOG} after analog clone" >&2
  exit 1
fi
echo "guardrails analog $(git -C docs/guardrails rev-parse --short HEAD)"
