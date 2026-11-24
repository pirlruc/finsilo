#!/usr/bin/env bash
# Robolectric fetches android-all jars itself. Put the jars the unit tests
# use into the local Maven repository before the suite starts.
set -euo pipefail

versions=(
	6.0.1_r3-robolectric-r1-i7
	8.0.0_r4-robolectric-r1-i7
	9-robolectric-4913185-2-i7
	14-robolectric-10818077-i7
	17-robolectric-15733970-i7
)
base="https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented"
dest="${HOME}/.m2/repository/org/robolectric/android-all-instrumented"

for version in "${versions[@]}"; do
	dir="${dest}/${version}"
	mkdir -p "${dir}"
	name="android-all-instrumented-${version}"
	for suffix in jar pom jar.sha512 pom.sha512; do
		if [[ ! -s "${dir}/${name}.${suffix}" ]]; then
			curl -fsSL -o "${dir}/${name}.${suffix}" "${base}/${version}/${name}.${suffix}"
		fi
	done
done
