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
# A stale/invalid token must not fail the job: analog-pins already asserts the
# gitlink SHA, and threshold scripts fall back to the consumer copy.
if ! git -c "url.https://x-access-token:${GUARDRAILS_READ_TOKEN}@github.com/pirlruc/guardrails.git.insteadOf=https://github.com/pirlruc/guardrails.git" \
  submodule update --init docs/guardrails; then
  echo "GUARDRAILS_READ_TOKEN could not clone analog; reading config/kotlin.profile.thresholds.yml" >&2
  exit 0
fi
git -C docs/guardrails fetch --tags --force origin >/dev/null 2>&1 || true

if [[ ! -f "${ANALOG}" ]]; then
  echo "missing ${ANALOG} after analog clone" >&2
  exit 1
fi
echo "guardrails analog $(git -C docs/guardrails rev-parse --short HEAD)"
