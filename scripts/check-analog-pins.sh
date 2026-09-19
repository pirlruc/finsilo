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
