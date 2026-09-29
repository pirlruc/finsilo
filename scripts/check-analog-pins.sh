#!/usr/bin/env bash
# SC-DEP-004: documented bootstrap ref, gitlink SHA, and annotated tag agree.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# analog-pins.env is KEY=value, not a shell library.
# shellcheck disable=SC1091
. "$ROOT/scripts/analog-pins.env"

assert_gitlink() {
  local path="$1"
  local expected="$2"
  local ref="$3"
  local gitlink
  gitlink="$(git rev-parse ":${path}")"
  if [[ "$gitlink" != "$expected" ]]; then
    echo "error: $path gitlink $gitlink != documented $expected ($ref)" >&2
    exit 1
  fi
  if { [[ -d "$path/.git" ]] || [[ -f "$path/.git" ]]; } && git -C "$path" rev-parse "${ref}^{commit}" >/dev/null 2>&1; then
    local peeled
    peeled="$(git -C "$path" rev-parse "${ref}^{commit}")"
    if [[ "$peeled" != "$gitlink" ]]; then
      echo "error: $path tag $ref peels to $peeled != gitlink $gitlink" >&2
      exit 1
    fi
  fi
  echo "$path $ref -> $gitlink"
}

assert_gitlink docs/guardrails "$GUARDRAILS_SHA" "$GUARDRAILS_REF"
assert_gitlink .github/scaffold "$SCAFFOLD_SHA" "$SCAFFOLD_REF"

# No workflow may checkout an ops repo. common-infra-lint, common-doc-verify,
# common-secrets-sast, and common-supply-chain do that inside the reusable
# workflow, so those uses: lines are rejected. scaffold-verify and scorecard
# check out the caller only.
if grep -RInE 'repository:[[:space:]]*pirlruc/(commondevops|containerdevops|cppdevops|pydevops)' .github/workflows; then
  echo "error: CI workflow checks out an ops repo" >&2
  exit 1
fi
uses_hit="$(grep -RInE 'uses:[[:space:]]*pirlruc/(commondevops|containerdevops|cppdevops|pydevops)/' .github/workflows || true)"
allowed="$(printf '%s\n' \
  "uses: pirlruc/commondevops/.github/workflows/common-scaffold-verify.yml@${COMMONDEVOPS_SHA}" \
  "uses: pirlruc/commondevops/.github/workflows/common-scorecard.yml@${COMMONDEVOPS_SHA}" | sort)"
actual="$(printf '%s\n' "$uses_hit" | sed 's/^[^:]*:[0-9]*://' | sed 's/^[[:space:]]*//' | sort)"
if [[ "$actual" != "$allowed" ]]; then
  echo "error: ops uses: pins != scaffold-verify and scorecard at ${COMMONDEVOPS_REF} ${COMMONDEVOPS_SHA}" >&2
  printf '%s\n' "$uses_hit" >&2
  exit 1
fi
echo "commondevops ${COMMONDEVOPS_REF} callers do not checkout that repo"
