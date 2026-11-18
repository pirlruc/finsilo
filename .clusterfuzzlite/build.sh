#!/bin/bash -eu
cd "${SRC}/finsilo"
chmod +x ./gradlew
./gradlew :domain:testClasses --no-daemon
find domain/build -name 'domain-*.jar' -o -name 'test-fixtures*.jar' | head
echo "Jazzer targets: Trading212CsvFuzz DegiroTransactionsFuzz DegiroAccountFuzz RevolutStocksFuzz RevolutCryptoFuzz RevolutCommodityFuzz"
