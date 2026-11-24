#!/bin/bash -eu
# Builds Jazzer JVM targets for the broker CSV, Revolut, and IBKR parsers.
cd "${SRC}/finsilo"
chmod +x ./gradlew
./gradlew :domain:jar :domain:testClasses :domain:jazzerClasspath --no-daemon --quiet > "${OUT}/classpath.txt"

mkdir -p "${OUT}/classpath"
while IFS= read -r entry; do
  [[ -z "${entry}" ]] && continue
  if [[ -d "${entry}" ]]; then
    name="$(basename "${entry}")-$(basename "$(dirname "${entry}")")"
    rm -rf "${OUT}/classpath/${name}"
    cp -a "${entry}" "${OUT}/classpath/${name}"
  elif [[ -f "${entry}" ]]; then
    cp -a "${entry}" "${OUT}/classpath/"
  fi
done < "${OUT}/classpath.txt"

if [[ -n "${JAZZER_ROOT:-}" && -d "${JAZZER_ROOT}" ]]; then
  cp -a "${JAZZER_ROOT}/." "${OUT}/"
fi
if [[ ! -x "${OUT}/jazzer" && ! -x "${OUT}/jazzer_driver" ]]; then
  archive="$(mktemp)"
  curl -fsSL -o "${archive}" https://github.com/CodeIntelligenceTesting/jazzer/releases/download/v0.24.0/jazzer-linux.tar.gz
  tar -xzf "${archive}" -C "${OUT}"
  rm -f "${archive}"
fi
driver="${OUT}/jazzer"
if [[ ! -x "${driver}" ]]; then
  driver="${OUT}/jazzer_driver"
fi
test -x "${driver}"

cp_entries=()
while IFS= read -r copied; do
  cp_entries+=("${copied}")
done < <(find "${OUT}/classpath" -mindepth 1 -maxdepth 1 | sort)
classpath="$(printf '%s:' "${cp_entries[@]}")"
classpath="${classpath%:}"
test -n "${classpath}"

targets=(
  com.pirlruc.finsilo.domain.importcsv.BrokerCsvFuzz
  com.pirlruc.finsilo.domain.importcsv.RevolutStocksFuzz
  com.pirlruc.finsilo.domain.importcsv.RevolutCryptoFuzz
  com.pirlruc.finsilo.domain.importcsv.RevolutCommodityFuzz
  com.pirlruc.finsilo.domain.importcsv.IbkrTradesFuzz
  com.pirlruc.finsilo.domain.importcsv.OpenBbFundamentalsFuzz
)
for class_name in "${targets[@]}"; do
  class_file="$(find "${OUT}/classpath" -name "${class_name##*.}.class" | head -n 1)"
  test -n "${class_file}"
  cat > "${OUT}/${class_name}" << EOF
#!/bin/bash
set -eu
dir=\$(cd "\$(dirname "\$0")" && pwd)
exec "${driver}" --cp="${classpath}" --target_class=${class_name} "\$@"
EOF
  chmod +x "${OUT}/${class_name}"
done
