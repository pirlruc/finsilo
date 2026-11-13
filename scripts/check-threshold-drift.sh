#!/bin/sh
# Fail when vendored threshold keys disagree with the pinned guardrails submodule.
set -eu
ROOT="$(cd -- "$(dirname "$0")/.." && pwd)"

extract() {
	# Keep license_allow_list in the key compare. The value is checked by check_license_allow.
	grep -E '^[a-zA-Z0-9_]+:' "$1" | sed 's/[[:space:]]*#.*//' | sed 's/[[:space:]]*$//' | sed 's/^license_allow_list:.*/license_allow_list:/'
}

check_pair() {
	vended="$1"
	upstream="$2"
	label="$3"
	if [ ! -f "$vended" ] || [ ! -f "$upstream" ]; then
		echo "error: missing $label" >&2
		return 1
	fi
	tmp_u="$(mktemp)"
	tmp_v="$(mktemp)"
	extract "$upstream" >"$tmp_u"
	extract "$vended" >"$tmp_v"
	if ! diff -u "$tmp_u" "$tmp_v"; then
		rm -f "$tmp_u" "$tmp_v"
		echo "error: $label drifted from $upstream" >&2
		return 1
	fi
	rm -f "$tmp_u" "$tmp_v"
	echo "Thresholds in sync: $label"
}

check_license_allow() {
	python3 - "$1" "$2" <<'PY'
import sys
from pathlib import Path

def items(path, key):
    values = []
    in_list = False
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        stripped = line.strip()
        if not in_list:
            if not stripped.startswith(f"{key}:"):
                continue
            rest = stripped.split(":", 1)[1].strip()
            if rest == "[]":
                return values
            if rest == "":
                in_list = True
                continue
            if rest.startswith("["):
                body = rest.strip("[]")
                return [part.strip().strip("'\"") for part in body.split(",") if part.strip()]
            in_list = True
            continue
        if stripped.startswith("- "):
            values.append(stripped[2:].strip().strip("'\""))
            continue
        if stripped.endswith(":") or not stripped:
            break
    return values

upstream, vended = sys.argv[1], sys.argv[2]
analog = items(upstream, "license_allow_list")
consumer = items(vended, "license_allow_list")
deny = items(vended, "license_deny_list")
for allowed in consumer:
    for needle in deny:
        if needle and needle.lower() in allowed.lower():
            print(f"error: {allowed} is also on the deny list ({needle})", file=sys.stderr)
            sys.exit(1)
if not analog and consumer:
    print("license_allow_list is stricter than the empty analog list")
    sys.exit(0)
if sorted(analog) != sorted(consumer):
    print("error: license_allow_list drifted", file=sys.stderr)
    sys.exit(1)
print("license_allow_list in sync")
PY
}

check_pair "$ROOT/config/kotlin.profile.thresholds.yml" "$ROOT/docs/guardrails/kotlin/profile.thresholds.yml" "kotlin"
check_pair "$ROOT/config/android.profile.thresholds.yml" "$ROOT/docs/guardrails/android/profile.thresholds.yml" "android"
check_pair "$ROOT/config/supply-chain.profile.thresholds.yml" "$ROOT/docs/guardrails/supply-chain/profile.thresholds.yml" "supply-chain"
check_license_allow "$ROOT/docs/guardrails/supply-chain/profile.thresholds.yml" "$ROOT/config/supply-chain.profile.thresholds.yml"
