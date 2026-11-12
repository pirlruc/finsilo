#!/bin/sh
# Fail when vendored threshold keys disagree with the pinned guardrails submodule.
set -eu
ROOT="$(cd -- "$(dirname "$0")/.." && pwd)"

extract() {
	# license_allow_list is the consumer's third-party SPDX set. The analog ships [].
	grep -E '^[a-zA-Z0-9_]+:' "$1" | sed 's/[[:space:]]*#.*//' | sed 's/[[:space:]]*$//' | grep -v '^license_allow_list:'
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

check_pair "$ROOT/config/kotlin.profile.thresholds.yml" "$ROOT/docs/guardrails/kotlin/profile.thresholds.yml" "kotlin"
check_pair "$ROOT/config/android.profile.thresholds.yml" "$ROOT/docs/guardrails/android/profile.thresholds.yml" "android"
check_pair "$ROOT/config/supply-chain.profile.thresholds.yml" "$ROOT/docs/guardrails/supply-chain/profile.thresholds.yml" "supply-chain"
